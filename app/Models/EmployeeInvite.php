<?php

declare(strict_types=1);

namespace App\Models;

use App\Core\Database;
use Throwable;

class EmployeeInvite
{
    public static function create(int $shopId, int $createdBy, array $permissions, int $hoursValid = 72): array
    {
        $pdo = Database::connect();
        $token = bin2hex(random_bytes(24));
        $expiresAt = (new \DateTimeImmutable("+{$hoursValid} hours"))->format('Y-m-d H:i:s');

        $stmt = $pdo->prepare(
            'INSERT INTO employee_invites (shop_id, token, preset_permissions_json, created_by, expires_at)
             VALUES (?, ?, ?, ?, ?)'
        );
        $stmt->execute([$shopId, $token, json_encode(array_values($permissions)), $createdBy, $expiresAt]);

        return ['id' => (int) $pdo->lastInsertId(), 'token' => $token, 'expires_at' => $expiresAt];
    }

    public static function findValidByToken(string $token): ?array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM employee_invites
             WHERE token = ? AND used_at IS NULL AND expires_at > datetime('now')"
        );
        $stmt->execute([$token]);

        return $stmt->fetch() ?: null;
    }

    /**
     * Atomically claims the invite (token, shop info, permissions) and creates the employee
     * account for it, all within a single transaction — this closes the TOCTOU window where two
     * near-simultaneous requests with the same one-time invite link could both pass a plain
     * "is it still valid" SELECT before either one marked it used, letting the same invite create
     * two employee accounts.
     *
     * The UPDATE below is the only thing that matters for correctness: it flips used_at from NULL
     * to now() in one atomic statement, guarded by the same validity conditions a plain read would
     * check. Whichever concurrent request's UPDATE runs first wins the claim (rowCount() === 1);
     * the other sees rowCount() === 0 and is rejected as invalid/expired/already-used, exactly like
     * a token that was never valid to begin with.
     *
     * $userData is passed through to User::create(), with shop_id/role/permissions_json filled in
     * from the claimed invite. Returns the new user's id, or null if the token could not be claimed.
     * If User::create() throws (e.g. duplicate login slipping through a race), the whole
     * transaction is rolled back so the invite is NOT left consumed with no employee created.
     *
     * @throws Throwable re-thrown after rollback when user creation fails for a reason other than
     *                    an invalid/expired/already-used invite
     */
    public static function claimAndRegister(string $token, array $userData): ?int
    {
        $pdo = Database::connect();
        $pdo->beginTransaction();

        try {
            $claim = $pdo->prepare(
                "UPDATE employee_invites SET used_at = datetime('now')
                 WHERE token = ? AND used_at IS NULL AND expires_at > datetime('now')"
            );
            $claim->execute([$token]);

            if ($claim->rowCount() !== 1) {
                $pdo->rollBack();
                return null;
            }

            $select = $pdo->prepare('SELECT * FROM employee_invites WHERE token = ?');
            $select->execute([$token]);
            $invite = $select->fetch();

            $userData['shop_id'] = (int) $invite['shop_id'];
            $userData['role'] = 'employee';
            $userData['permissions_json'] = $invite['preset_permissions_json'];

            $userId = User::create($userData);

            $pdo->prepare('UPDATE employee_invites SET used_by = ? WHERE id = ?')
                ->execute([$userId, $invite['id']]);

            $pdo->commit();

            return $userId;
        } catch (Throwable $e) {
            $pdo->rollBack();
            throw $e;
        }
    }

    public static function pendingByShop(int $shopId): array
    {
        $stmt = Database::connect()->prepare(
            "SELECT * FROM employee_invites
             WHERE shop_id = ? AND used_at IS NULL AND expires_at > datetime('now')
             ORDER BY created_at DESC"
        );
        $stmt->execute([$shopId]);

        return $stmt->fetchAll();
    }

    public static function find(int $id, int $shopId): ?array
    {
        $stmt = Database::connect()->prepare('SELECT * FROM employee_invites WHERE id = ? AND shop_id = ?');
        $stmt->execute([$id, $shopId]);

        return $stmt->fetch() ?: null;
    }

    public static function revoke(int $id, int $shopId): void
    {
        Database::connect()
            ->prepare('DELETE FROM employee_invites WHERE id = ? AND shop_id = ?')
            ->execute([$id, $shopId]);
    }
}
