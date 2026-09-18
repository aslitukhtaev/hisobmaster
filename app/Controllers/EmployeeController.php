<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
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
}
