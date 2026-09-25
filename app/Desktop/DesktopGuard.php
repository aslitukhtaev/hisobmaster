<?php

declare(strict_types=1);

namespace App\Desktop;

use App\Core\Request;

/**
 * Runs before every request in the desktop app (public/index.php): sends a
 * computer that hasn't been activated to the activation page, and while the
 * license isn't valid (see Desktop::licenseStatus()) refuses anything that
 * would record new data — viewing, reports and syncing keep working.
 */
final class DesktopGuard
{
    /** Always reachable: activation, syncing, signing in/out, language. */
    private const OPEN_PREFIXES = ['/desktop/', '/lang/'];
    private const OPEN_POSTS = ['/login', '/logout'];

    public static function handle(Request $request): void
    {
        if (!Desktop::enabled()) {
            return;
        }

        $path = $request->path();
        foreach (self::OPEN_PREFIXES as $prefix) {
            if (str_starts_with($path, $prefix)) {
                return;
            }
        }

        if (!Desktop::isActivated()) {
            redirect('/desktop/activate');
        }

        Desktop::touchClock();

        if ($request->method() !== 'POST' || in_array($path, self::OPEN_POSTS, true)) {
            return;
        }

        $state = Desktop::licenseStatus()['state'];
        if ($state !== 'ok') {
            flash('error', t('desktop_locked_' . $state));
            $back = parse_url((string) ($_SERVER['HTTP_REFERER'] ?? '/'), PHP_URL_PATH) ?: '/';
            redirect($back);
        }
    }
}
