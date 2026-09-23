<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Customer;
use App\Models\DebtTransaction;
use App\Models\Sale;
use App\Models\Shop;

class CustomerController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $onlyDebtors = $request->input('filter', 'debtors') === 'debtors';

        View::render('customers/index', [
            'customers' => Customer::allWithBalance($shopId, $onlyDebtors),
            'onlyDebtors' => $onlyDebtors,
            'totalDebt' => DebtTransaction::totalDebtByShop($shopId),
        ]);
    }

    public function createForm(Request $request): void
    {
        View::render('customers/create');
    }

    public function store(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        $fullName = trim((string) $request->input('full_name', ''));
        $phone = trim((string) $request->input('phone', ''));
        $note = trim((string) $request->input('note', ''));

        if ($fullName === '') {
            flash('error', t('fill_required_fields'));
            keep_old(['full_name' => $fullName, 'phone' => $phone, 'note' => $note]);
            redirect('/customers/create');
        }

        $normalizedPhone = normalize_phone($phone);
        if ($normalizedPhone === null) {
            flash('error', t('phone_invalid'));
            keep_old(['full_name' => $fullName, 'phone' => $phone, 'note' => $note]);
            redirect('/customers/create');
        }
        $phone = $normalizedPhone;

        $id = Customer::create($shopId, $fullName, $phone !== '' ? $phone : null, $note !== '' ? $note : null);

        flash('success', t('customer_created'));
        redirect("/customers/{$id}");
    }

    public function show(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $customer = Customer::find((int) $id, $shopId);

        if (!$customer) {
            abort_404();
        }

        $balance = DebtTransaction::currentBalance((int) $id);

        // Composed here (not in the view) so the exact wording that will be
        // sent through the owner's own SMS/WhatsApp/Telegram share links (see
        // customers/show.php + debt-reminder.js) comes from one place. Only
        // built when there's actually a debt to remind about.
        $reminderMessage = '';
        if ($balance > 0) {
            $shop = Shop::find($shopId);
            $shopName = $shop['name'] ?? t('app_name');

            $reminderMessage = !empty($customer['debt_due_date'])
                ? t('debt_reminder_message_with_due', [
                    'shop' => $shopName,
                    'customer' => $customer['full_name'],
                    'amount' => money($balance),
                    'due_date' => $customer['debt_due_date'],
                ])
                : t('debt_reminder_message_no_due', [
                    'shop' => $shopName,
                    'customer' => $customer['full_name'],
                    'amount' => money($balance),
                ]);
        }

        View::render('customers/show', [
            'customer' => $customer,
            'balance' => $balance,
            'history' => DebtTransaction::historyByCustomer((int) $id),
            'purchases' => Sale::byCustomer((int) $id, $shopId),
            'reminderMessage' => $reminderMessage,
        ]);
    }

    public function editForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $customer = Customer::find((int) $id, $shopId);

        if (!$customer) {
            abort_404();
        }

        View::render('customers/edit', ['customer' => $customer]);
    }

    public function update(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $customer = Customer::find((int) $id, $shopId);

        if (!$customer) {
            flash('error', t('customer_not_found'));
            redirect('/customers');
        }

        $fullName = trim((string) $request->input('full_name', ''));
        $phone = trim((string) $request->input('phone', ''));
        $note = trim((string) $request->input('note', ''));
        $creditLimitInput = trim((string) $request->input('credit_limit', ''));
        $debtDueDateInput = trim((string) $request->input('debt_due_date', ''));

        $old = [
            'full_name' => $fullName,
            'phone' => $phone,
            'note' => $note,
            'credit_limit' => $creditLimitInput,
            'debt_due_date' => $debtDueDateInput,
        ];

        if ($fullName === '') {
            flash('error', t('fill_required_fields'));
            keep_old($old);
            redirect("/customers/{$id}/edit");
        }

        $normalizedPhone = normalize_phone($phone);
        if ($normalizedPhone === null) {
            flash('error', t('phone_invalid'));
            keep_old($old);
            redirect("/customers/{$id}/edit");
        }
        $phone = $normalizedPhone;

        $creditLimit = null;
        if ($creditLimitInput !== '') {
            if (!valid_money($creditLimitInput)) {
                flash('error', t('credit_limit_invalid'));
                keep_old($old);
                redirect("/customers/{$id}/edit");
            }
            $creditLimit = (float) $creditLimitInput;
        }

        $debtDueDate = $debtDueDateInput !== '' ? $debtDueDateInput : null;

        Customer::update(
            (int) $id,
            $shopId,
            $fullName,
            $phone !== '' ? $phone : null,
            $note !== '' ? $note : null,
            $creditLimit,
            $debtDueDate
        );

        flash('success', t('customer_updated'));
        redirect("/customers/{$id}");
    }

    public function recordPayment(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $customer = Customer::find((int) $id, $shopId);

        if (!$customer) {
            flash('error', t('customer_not_found'));
            redirect('/customers');
        }

        $amountRaw = $request->input('amount', 0);
        $amount = is_numeric($amountRaw) && valid_money($amountRaw) ? (float) $amountRaw : 0.0;
        $balance = DebtTransaction::currentBalance((int) $id);

        if ($amount <= 0) {
            flash('error', t('payment_amount_invalid'));
            redirect("/customers/{$id}");
        }

        if ($amount > $balance) {
            flash('error', t('payment_exceeds_debt'));
            redirect("/customers/{$id}");
        }

        DebtTransaction::record($shopId, (int) $id, null, 'tolov', $amount, (int) Auth::id());

        ActivityLog::record($shopId, (int) Auth::id(), 'debt_payment_recorded', [
            'amount' => money($amount),
            'customer_name' => $customer['full_name'],
        ]);

        flash('success', t('payment_recorded'));
        redirect("/customers/{$id}");
    }
}
