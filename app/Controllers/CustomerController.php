<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Customer;
use App\Models\DebtTransaction;

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

        $id = Customer::create($shopId, $fullName, $phone !== '' ? $phone : null, $note !== '' ? $note : null);

        flash('success', t('customer_created'));
        redirect("/customers/{$id}");
    }

    public function show(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $customer = Customer::find((int) $id, $shopId);

        if (!$customer) {
            flash('error', t('customer_not_found'));
            redirect('/customers');
        }

        View::render('customers/show', [
            'customer' => $customer,
            'balance' => DebtTransaction::currentBalance((int) $id),
            'history' => DebtTransaction::historyByCustomer((int) $id),
        ]);
    }

    public function editForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $customer = Customer::find((int) $id, $shopId);

        if (!$customer) {
            flash('error', t('customer_not_found'));
            redirect('/customers');
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

        if ($fullName === '') {
            flash('error', t('fill_required_fields'));
            keep_old(['full_name' => $fullName, 'phone' => $phone, 'note' => $note]);
            redirect("/customers/{$id}/edit");
        }

        Customer::update((int) $id, $shopId, $fullName, $phone !== '' ? $phone : null, $note !== '' ? $note : null);

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

        $amount = (float) $request->input('amount', 0);
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

        flash('success', t('payment_recorded'));
        redirect("/customers/{$id}");
    }
}
