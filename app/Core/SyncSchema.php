<?php

declare(strict_types=1);

namespace App\Core;

/**
 * Which tables the desktop app keeps in sync with the server.
 *
 * Every row in these tables carries a `uuid` (filled in by a trigger on
 * insert, see Migrator::addSyncIdentity()): the integer `id` is only
 * meaningful inside one database — a sale made offline on a shop's computer
 * gets whatever id is next there, which can already be taken on the server —
 * so a row is identified across databases by its uuid instead.
 *
 * Not listed on purpose: shops and settings (one row / a natural key per
 * shop), employee_invites (online-only).
 */
final class SyncSchema
{
    public const TABLES = [
        'users',
        'attendance',
        'categories',
        'products',
        'product_variants',
        'suppliers',
        'purchases',
        'purchase_items',
        'customers',
        'sales',
        'sale_items',
        'sale_payments',
        'expenses',
        'debt_transactions',
        'refunds',
        'refund_items',
        'refund_payments',
        'activity_log',
    ];
}
