<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Attendance;
use App\Models\Report;

class AttendanceController
{
    /**
     * "Ishga keldim" — any logged-in shop user (owner or employee), not
     * gated by a permission like sales/products/etc. since clocking in
     * isn't a business-data permission. Rejected server-side (not just by
     * hiding the button) when the user already has an open shift — see
     * Attendance::clockIn().
     */
    public function clockIn(Request $request): void
    {
        $shopId = Auth::shopId();
        if ($shopId !== null) {
            $shift = Attendance::clockIn((int) $shopId, (int) Auth::id());
            flash($shift !== null ? 'success' : 'error', $shift !== null
                ? t('attendance_clock_in_recorded')
                : t('attendance_already_clocked_in'));
        }

        redirect($_SERVER['HTTP_REFERER'] ?? '/');
    }

    /**
     * "Ishni tugatdim". See Attendance::clockOut() for the double-clock-out
     * guard.
     */
    public function clockOut(Request $request): void
    {
        $closed = Attendance::clockOut((int) Auth::id());
        flash($closed ? 'success' : 'error', $closed
            ? t('attendance_clock_out_recorded')
            : t('attendance_no_open_shift'));

        redirect($_SERVER['HTTP_REFERER'] ?? '/');
    }

    /**
     * The owner-only attendance roster for the shop — every employee's
     * clock-in/out history and shift duration for the selected period. Not
     * available to a plain employee account (see routes.php's role:owner
     * gate), same as every other employee-management page.
     */
    public function history(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $period = (string) $request->input('period', 'week');
        $customFrom = (string) $request->input('from', '');
        $customTo = (string) $request->input('to', '');

        [$from, $to, $period] = Report::resolvePeriod($period, $customFrom, $customTo);

        View::render('employees/attendance', [
            'rows' => Attendance::historyByShop($shopId, $from, $to),
            'openCount' => Attendance::openCountByShop($shopId),
            'period' => $period,
            'from' => $from,
            'to' => $to,
        ]);
    }
}
