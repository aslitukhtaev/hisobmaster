<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Category;
use App\Models\Expense;

class ExpenseController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $from = (string) $request->input('from', date('Y-m-01'));
        $to = (string) $request->input('to', date('Y-m-d'));

        View::render('expenses/index', [
            'expenses' => Expense::allByShop($shopId, $from, $to),
            'total' => Expense::totalByShop($shopId, $from, $to),
            'from' => $from,
            'to' => $to,
        ]);
    }

    public function createForm(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('expenses/create', [
            'categories' => Category::allByShop($shopId, 'expense'),
        ]);
    }

    public function store(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $data = $this->validate($request);

        if ($data === null) {
            redirect('/expenses/create');
        }

        $categoryId = $data['category'] !== ''
            ? Category::findOrCreate($shopId, $data['category'], 'expense')
            : null;

        Expense::create([
            'shop_id' => $shopId,
            'category_id' => $categoryId,
            'amount' => $data['amount'],
            'description' => $data['description'],
            'expense_date' => $data['expense_date'],
            'created_by' => (int) Auth::id(),
        ]);

        flash('success', t('expense_created'));
        redirect('/expenses');
    }

    public function editForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $expense = Expense::find((int) $id, $shopId);

        if (!$expense) {
            flash('error', t('expense_not_found'));
            redirect('/expenses');
        }

        View::render('expenses/edit', [
            'expense' => $expense,
            'categories' => Category::allByShop($shopId, 'expense'),
        ]);
    }

    public function update(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $expense = Expense::find((int) $id, $shopId);

        if (!$expense) {
            flash('error', t('expense_not_found'));
            redirect('/expenses');
        }

        $data = $this->validate($request);

        if ($data === null) {
            redirect("/expenses/{$id}/edit");
        }

        $categoryId = $data['category'] !== ''
            ? Category::findOrCreate($shopId, $data['category'], 'expense')
            : null;

        Expense::update((int) $id, $shopId, [
            'category_id' => $categoryId,
            'amount' => $data['amount'],
            'description' => $data['description'],
            'expense_date' => $data['expense_date'],
        ]);

        flash('success', t('expense_updated'));
        redirect('/expenses');
    }

    public function delete(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $expense = Expense::find((int) $id, $shopId);

        if ($expense) {
            Expense::delete((int) $id, $shopId);
            flash('success', t('expense_deleted'));
        }

        redirect('/expenses');
    }

    /**
     * @return array{category: string, amount: float, description: ?string, expense_date: string}|null
     */
    private function validate(Request $request): ?array
    {
        $category = trim((string) $request->input('category', ''));
        $amount = $request->input('amount', '');
        $description = trim((string) $request->input('description', ''));
        $expenseDate = trim((string) $request->input('expense_date', '')) ?: date('Y-m-d');

        $old = [
            'category' => $category,
            'amount' => (string) $amount,
            'description' => $description,
            'expense_date' => $expenseDate,
        ];

        if (!is_numeric($amount) || (float) $amount <= 0) {
            flash('error', t('values_must_be_positive'));
            keep_old($old);
            return null;
        }

        return [
            'category' => $category,
            'amount' => (float) $amount,
            'description' => $description !== '' ? $description : null,
            'expense_date' => $expenseDate,
        ];
    }
}
