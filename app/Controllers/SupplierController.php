<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\Supplier;

class SupplierController
{
    public function index(Request $request): void
    {
        $shopId = (int) Auth::shopId();

        View::render('suppliers/index', [
            'suppliers' => Supplier::allByShop($shopId),
        ]);
    }

    public function createForm(Request $request): void
    {
        View::render('suppliers/create');
    }

    public function store(Request $request): void
    {
        $shopId = (int) Auth::shopId();
        $data = $this->validate($request);

        if ($data === null) {
            redirect('/suppliers/create');
        }

        $id = Supplier::create($shopId, $data['name'], $data['phone'], $data['address'], $data['note']);

        flash('success', t('supplier_created'));
        redirect('/suppliers');
    }

    public function editForm(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $supplier = Supplier::find((int) $id, $shopId);

        if (!$supplier) {
            flash('error', t('supplier_not_found'));
            redirect('/suppliers');
        }

        View::render('suppliers/edit', ['supplier' => $supplier]);
    }

    public function update(Request $request, string $id): void
    {
        $shopId = (int) Auth::shopId();
        $supplier = Supplier::find((int) $id, $shopId);

        if (!$supplier) {
            flash('error', t('supplier_not_found'));
            redirect('/suppliers');
        }

        $data = $this->validate($request);
        if ($data === null) {
            redirect("/suppliers/{$id}/edit");
        }

        Supplier::update((int) $id, $shopId, $data['name'], $data['phone'], $data['address'], $data['note']);

        flash('success', t('supplier_updated'));
        redirect('/suppliers');
    }

    /**
     * @return array{name: string, phone: ?string, address: ?string, note: ?string}|null
     */
    private function validate(Request $request): ?array
    {
        $name = trim((string) $request->input('name', ''));
        $phone = trim((string) $request->input('phone', ''));
        $address = trim((string) $request->input('address', ''));
        $note = trim((string) $request->input('note', ''));

        if ($name === '') {
            flash('error', t('fill_required_fields'));
            keep_old(['name' => $name, 'phone' => $phone, 'address' => $address, 'note' => $note]);
            return null;
        }

        return [
            'name' => $name,
            'phone' => $phone !== '' ? $phone : null,
            'address' => $address !== '' ? $address : null,
            'note' => $note !== '' ? $note : null,
        ];
    }
}
