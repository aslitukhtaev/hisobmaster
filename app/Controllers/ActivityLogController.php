<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\User;

class ActivityLogController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $filters = $this->readFilters($request);

        View::render('activity/index', [
            'entries' => ActivityLog::filtered(
                $shopId,
                $filters['user_id'],
                $filters['action'],
                $filters['from_utc'],
                $filters['to_utc'],
                $filters['search']
            ),
            'users' => User::allByShop($shopId),
            'actions' => ActivityLog::KNOWN_ACTIONS,
            'filters' => $filters,
        ]);
    }

    /**
     * Same filters as index(), streamed as CSV instead of rendered as a
     * table — mirrors ReportController::exportCsv()'s plain fputcsv()
     * approach, no library involved.
     */
    public function exportCsv(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $filters = $this->readFilters($request);

        $entries = ActivityLog::filtered(
            $shopId,
            $filters['user_id'],
            $filters['action'],
            $filters['from_utc'],
            $filters['to_utc'],
            $filters['search']
        );

        header('Content-Type: text/csv; charset=UTF-8');
        header('Content-Disposition: attachment; filename="faoliyat_tarixi_' . date('Y-m-d') . '.csv"');
        header('Cache-Control: no-store');

        $out = fopen('php://output', 'w');
        // UTF-8 BOM so Excel opens Uzbek/Cyrillic text correctly.
        fwrite($out, "\xEF\xBB\xBF");

        csv_row($out, [t('sale_date'), t('full_name_label'), t('activity_description')]);
        foreach ($entries as $entry) {
            $meta = json_decode($entry['meta_json'] ?? '[]', true) ?: [];
            csv_row($out, [
                local_datetime($entry['created_at']),
                $entry['user_name'] ?? '—',
                t('activity_' . $entry['action'], $meta),
            ]);
        }

        fclose($out);
        exit;
    }

    /**
     * @return array{user_id: ?int, action: ?string, from: string, to: string, search: string, from_utc: ?string, to_utc: ?string}
     */
    private function readFilters(Request $request): array
    {
        $userId = (string) $request->input('user_id', '');
        $action = (string) $request->input('action', '');
        $from = (string) $request->input('from', '');
        $to = (string) $request->input('to', '');
        $search = trim((string) $request->input('q', ''));

        $fromUtc = null;
        $toUtc = null;
        if ($from !== '' && $to !== '' && $from <= $to) {
            [$fromUtc, $toUtc] = tashkent_day_bounds_utc($from, $to);
        } else {
            // An invalid/partial range is treated as "no date filter" rather
            // than silently swapped or clamped — kept out of the SQL WHERE
            // entirely so it can't accidentally exclude every row.
            $from = '';
            $to = '';
        }

        return [
            'user_id' => $userId !== '' ? (int) $userId : null,
            'action' => $action !== '' ? $action : null,
            'from' => $from,
            'to' => $to,
            'search' => $search,
            'from_utc' => $fromUtc,
            'to_utc' => $toUtc,
        ];
    }
}
