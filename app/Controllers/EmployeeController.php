<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\EmployeeInvite;
use App\Models\User;

class EmployeeController
{
    public const PERMISSIONS = ['sales', 'products', 'prices', 'customers', 'expenses', 'reports'];

    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('employees/index', [
            'employees' => User::employeesByShop($shopId),
            'invites' => EmployeeInvite::pendingByShop($shopId),
            'permissions' => self::PERMISSIONS,
        ]);
    }

    public function inviteForm(Request $request): void
    {
        View::render('employees/invite', ['permissions' => self::PERMISSIONS]);
    }

    public function createInvite(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $permissions = $this->readPermissions($request);

        $invite = EmployeeInvite::create($shopId, (int) Auth::id(), $permissions);
        $link = base_url('/join/' . $invite['token']);

        View::render('employees/invite-created', [
            'link' => $link,
            'expiresAt' => $invite['expires_at'],
        ]);
    }

    public function revokeInvite(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        EmployeeInvite::revoke((int) $id, $shopId);

        flash('success', t('invite_revoked'));
        redirect('/employees');
    }

    public function editPermissions(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $employee = User::findEmployee((int) $id, $shopId);

        if (!$employee) {
            flash('error', t('employee_not_found'));
            redirect('/employees');
        }

        View::render('employees/edit', [
            'employee' => $employee,
            'permissions' => self::PERMISSIONS,
            'employeePermissions' => json_decode($employee['permissions_json'] ?? '[]', true) ?: [],
        ]);
    }

    public function updatePermissions(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $employee = User::findEmployee((int) $id, $shopId);

        if (!$employee) {
            flash('error', t('employee_not_found'));
            redirect('/employees');
        }

        User::updatePermissions((int) $id, $this->readPermissions($request));
        User::updateCommissionRate((int) $id, $this->readCommissionRate($request));

        ActivityLog::record($shopId, (int) Auth::id(), 'employee_permissions_updated', [
            'employee_name' => $employee['full_name'],
        ]);

        flash('success', t('employee_permissions_updated'));
        redirect('/employees');
    }

    public function toggleStatus(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $employee = User::findEmployee((int) $id, $shopId);

        if ($employee) {
            $newStatus = $employee['status'] === 'active' ? 'blocked' : 'active';
            User::setStatus((int) $id, $newStatus);
            ActivityLog::record($shopId, (int) Auth::id(), 'employee_status_changed', [
                'employee_name' => $employee['full_name'],
                'status' => $newStatus === 'active' ? t('active_status') : t('blocked_status'),
            ]);
            flash('success', t('employee_status_updated'));
        }

        redirect('/employees');
    }

    private function readPermissions(Request $request): array
    {
        $submitted = $request->input('permissions', []);
        if (!is_array($submitted)) {
            return [];
        }

        return array_values(array_intersect($submitted, self::PERMISSIONS));
    }

    /**
     * Blank input means "not eligible for commission" (null), never 0 — see
     * User::updateCommissionRate(). A non-numeric value is treated the same
     * as blank rather than rejecting the whole permissions save over it.
     * Clamped to a sane 0..100 percentage range.
     */
    private function readCommissionRate(Request $request): ?float
    {
        $raw = trim((string) $request->input('commission_rate', ''));
        if ($raw === '' || !is_numeric($raw)) {
            return null;
        }

        return max(0.0, min(100.0, (float) $raw));
    }
}
