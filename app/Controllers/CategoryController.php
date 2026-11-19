<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\ActivityLog;
use App\Models\Category;

/**
 * Product and expense categories: add, rename, merge two into one, delete.
 * Product categories need the "products" permission, expense ones "expenses".
 */
class CategoryController
{
    public function index(Request $request): void
    {
        $type = $this->type((string) $request->input('type', ''));
        $shopId = (int) Auth::shopId();

        View::render('categories/index', [
            'type' => $type,
            'types' => array_values(array_filter(Category::TYPES, [$this, 'allowed'])),
            'categories' => Category::withCounts($shopId, $type),
        ]);
    }

    public function store(Request $request): void
    {
        $type = $this->type((string) $request->input('type', ''));
        $shopId = (int) Auth::shopId();
        $name = $this->validName($request, $type);

        $existing = Category::findByName($shopId, $name, $type);
        if ($existing !== null) {
            flash('error', t('category_exists', ['name' => $existing['name']]));
            $this->back($type);
        }

        Category::create($shopId, $name, $type);
        ActivityLog::record($shopId, (int) Auth::id(), 'category_created', ['name' => Category::normalizeName($name)]);
        flash('success', t('category_created'));
        $this->back($type);
    }

    public function update(Request $request, string $id): void
    {
        [$category, $shopId] = $this->category($id);
        $type = $category['type'];
        $name = $this->validName($request, $type);

        $same = Category::findByName($shopId, $name, $type);
        if ($same !== null && (int) $same['id'] !== (int) $category['id']) {
            // Two categories may not share a name: the way to that is merging.
            flash('error', t('category_rename_taken', ['name' => $same['name']]));
            $this->back($type);
        }

        Category::rename((int) $category['id'], $shopId, $name);
        ActivityLog::record($shopId, (int) Auth::id(), 'category_renamed', ['from' => $category['name'], 'to' => Category::normalizeName($name)]);
        flash('success', t('category_saved'));
        $this->back($type);
    }

    public function merge(Request $request, string $id): void
    {
        [$category, $shopId] = $this->category($id);
        $type = $category['type'];
        $into = Category::find((int) $request->input('into', 0), $shopId);

        if ($into === null || $into['type'] !== $type || (int) $into['id'] === (int) $category['id']) {
            flash('error', t('category_merge_pick'));
            $this->back($type);
        }

        $moved = Category::merge((int) $category['id'], (int) $into['id'], $shopId);
        ActivityLog::record($shopId, (int) Auth::id(), 'category_merged', ['from' => $category['name'], 'into' => $into['name'], 'count' => (string) $moved]);
        flash('success', t('category_merged', ['from' => $category['name'], 'into' => $into['name']]));
        $this->back($type);
    }

    public function delete(Request $request, string $id): void
    {
        [$category, $shopId] = $this->category($id);

        Category::delete((int) $category['id'], $shopId);
        ActivityLog::record($shopId, (int) Auth::id(), 'category_deleted', ['name' => $category['name']]);
        flash('success', t('category_deleted', ['name' => $category['name']]));
        $this->back($category['type']);
    }

    private function allowed(string $type): bool
    {
        return can($type === 'expense' ? 'expenses' : 'products');
    }

    /** The asked-for type if the user may manage it, else the first one they may. */
    private function type(string $asked): string
    {
        if (in_array($asked, Category::TYPES, true) && $this->allowed($asked)) {
            return $asked;
        }
        foreach (Category::TYPES as $type) {
            if ($this->allowed($type)) {
                return $type;
            }
        }
        abort_403();
    }

    /** @return array{0: array, 1: int} */
    private function category(string $id): array
    {
        $shopId = (int) Auth::shopId();
        $category = Category::find((int) $id, $shopId);
        if ($category === null) {
            abort_404();
        }
        if (!$this->allowed($category['type'])) {
            abort_403();
        }

        return [$category, $shopId];
    }

    private function validName(Request $request, string $type): string
    {
        $name = Category::normalizeName((string) $request->input('name', ''));
        $error = null;
        if ($name === '') {
            $error = t('category_name_required');
        } elseif (mb_strlen($name) > 100) {
            $error = t('category_name_too_long');
        } elseif (looks_like_formula($name)) {
            // A name starting like a spreadsheet formula would run as one in an export.
            $error = t('category_name_formula');
        }
        if ($error !== null) {
            flash('error', $error);
            $this->back($type);
        }

        return $name;
    }

    private function back(string $type): never
    {
        redirect('/categories?type=' . $type);
    }
}
