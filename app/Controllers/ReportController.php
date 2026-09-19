<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Expense;
use App\Models\Report;
use App\Models\Shop;

class ReportController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $period = (string) $request->input('period', 'month');
        $customFrom = (string) $request->input('from', '');
        $customTo = (string) $request->input('to', '');
        $compare = (string) $request->input('compare', '') === '1';

        [$from, $to, $period] = $this->resolveRange($period, $customFrom, $customTo);

        $comparison = null;
        if ($compare) {
            [$prevFrom, $prevTo] = Report::previousPeriod($from, $to);
            $comparison = [
                'from' => $prevFrom,
                'to' => $prevTo,
                'summary' => Report::summary($shopId, $prevFrom, $prevTo),
            ];
        }

        View::render('reports/index', [
            'summary' => Report::summary($shopId, $from, $to),
            'topProducts' => Report::topProducts($shopId, $from, $to),
            'paymentBreakdown' => Report::paymentBreakdown($shopId, $from, $to),
            'dailyRevenue' => Report::dailyRevenue($shopId, $from, $to),
            'expenseBreakdown' => Expense::categoryBreakdown($shopId, $from, $to),
            'compare' => $compare,
            'comparison' => $comparison,
            'period' => $period,
            'from' => $from,
            'to' => $to,
        ]);
    }

    /**
     * A clean, print-optimized version of the same period's summary — the
     * "PDF export": window.print() + the app's existing @media print rules
     * (see app.css) turn this into a PDF through the browser's own print
     * dialog, with no PDF-generation library involved.
     */
    public function print(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $period = (string) $request->input('period', 'month');
        $customFrom = (string) $request->input('from', '');
        $customTo = (string) $request->input('to', '');

        [$from, $to] = $this->resolveRange($period, $customFrom, $customTo);

        View::render('reports/print', [
            'summary' => Report::summary($shopId, $from, $to),
            'topProducts' => Report::topProducts($shopId, $from, $to),
            'paymentBreakdown' => Report::paymentBreakdown($shopId, $from, $to),
            'expenseBreakdown' => Expense::categoryBreakdown($shopId, $from, $to),
            'shop' => Shop::find($shopId),
            'from' => $from,
            'to' => $to,
        ]);
    }

    /**
     * Streams the currently filtered report period as a .csv file — plain
     * fputcsv() over a few labeled sections, no library involved (see the
     * class-level constraint notes in CSV-export tickets across this repo).
     */
    public function exportCsv(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $period = (string) $request->input('period', 'month');
        $customFrom = (string) $request->input('from', '');
        $customTo = (string) $request->input('to', '');

        [$from, $to] = $this->resolveRange($period, $customFrom, $customTo);

        $summary = Report::summary($shopId, $from, $to);
        $paymentBreakdown = Report::paymentBreakdown($shopId, $from, $to);
        $expenseBreakdown = Expense::categoryBreakdown($shopId, $from, $to);
        $topProducts = Report::topProducts($shopId, $from, $to, 100);
        $dailyRevenue = Report::dailyRevenue($shopId, $from, $to);

        $filename = 'hisobot_' . $from . '_' . $to . '.csv';

        header('Content-Type: text/csv; charset=UTF-8');
        header('Content-Disposition: attachment; filename="' . $filename . '"');
        header('Cache-Control: no-store');

        $out = fopen('php://output', 'w');
        // A UTF-8 BOM so Excel (which otherwise guesses the system codepage)
        // opens Uzbek/Cyrillic text correctly instead of as mojibake.
        fwrite($out, "\xEF\xBB\xBF");

        $num = static fn (float $v): string => number_format($v, 2, '.', '');

        fputcsv($out, [t('reports'), $from . ' — ' . $to]);
        fputcsv($out, []);

        fputcsv($out, [t('sales_count_label'), t('total_revenue'), t('total_cogs'), t('expenses'), t('net_profit')]);
        fputcsv($out, [
            (int) $summary['sales_count'],
            $num((float) $summary['revenue']),
            $num((float) $summary['cogs']),
            $num((float) $summary['expenses']),
            $num((float) $summary['net_profit']),
        ]);
        fputcsv($out, []);

        fputcsv($out, [t('payment_breakdown_title')]);
        fputcsv($out, [t('payment_type'), t('amount')]);
        foreach (['naqd', 'karta', 'qarz'] as $type) {
            fputcsv($out, [t('payment_' . $type), $num((float) $paymentBreakdown[$type])]);
        }
        fputcsv($out, []);

        fputcsv($out, [t('expense_category_breakdown_title')]);
        fputcsv($out, [t('category'), t('amount')]);
        foreach ($expenseBreakdown as $row) {
            fputcsv($out, [$row['category_name'] ?? t('no_category'), $num($row['total'])]);
        }
        fputcsv($out, []);

        fputcsv($out, [t('top_products_title')]);
        fputcsv($out, [t('product_name'), t('qty_sold'), t('revenue')]);
        foreach ($topProducts as $row) {
            fputcsv($out, [$row['product_name'], format_qty((float) $row['qty_sold']), $num((float) $row['revenue'])]);
        }
        fputcsv($out, []);

        fputcsv($out, [t('daily_revenue_chart')]);
        fputcsv($out, [t('expense_date_label'), t('revenue')]);
        foreach ($dailyRevenue as $row) {
            fputcsv($out, [$row['date'], $num((float) $row['revenue'])]);
        }

        fclose($out);
        exit;
    }

    /**
     * @return array{0: string, 1: string, 2: string}
     */
    private function resolveRange(string $period, string $customFrom, string $customTo): array
    {
        return Report::resolvePeriod($period, $customFrom, $customTo);
    }
}
