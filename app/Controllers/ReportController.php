<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Report;
use DateTimeImmutable;

class ReportController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $period = (string) $request->input('period', 'month');
        $customFrom = (string) $request->input('from', '');
        $customTo = (string) $request->input('to', '');

        [$from, $to, $period] = $this->resolveRange($period, $customFrom, $customTo);

        View::render('reports/index', [
            'summary' => Report::summary($shopId, $from, $to),
            'topProducts' => Report::topProducts($shopId, $from, $to),
            'paymentBreakdown' => Report::paymentBreakdown($shopId, $from, $to),
            'dailyRevenue' => Report::dailyRevenue($shopId, $from, $to),
            'period' => $period,
            'from' => $from,
            'to' => $to,
        ]);
    }

    /**
     * @return array{0: string, 1: string, 2: string}
     */
    private function resolveRange(string $period, string $customFrom, string $customTo): array
    {
        if ($customFrom !== '' && $customTo !== '' && $customFrom <= $customTo) {
            return [$customFrom, $customTo, 'custom'];
        }

        $today = new DateTimeImmutable('today');
        $dayOfWeek = (int) $today->format('N'); // 1 (Monday) .. 7 (Sunday)
        $monday = $today->modify('-' . ($dayOfWeek - 1) . ' days');

        return match ($period) {
            'today' => [$today->format('Y-m-d'), $today->format('Y-m-d'), 'today'],
            'week' => [$monday->format('Y-m-d'), $today->format('Y-m-d'), 'week'],
            default => [$today->format('Y-m-01'), $today->format('Y-m-d'), 'month'],
        };
    }
}
