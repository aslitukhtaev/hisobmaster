<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Database;
use App\Core\RememberMe;
use App\Core\Request;
use App\Core\SocialAuth;
use App\Core\View;
use App\Desktop\Desktop;
use App\Models\ActivityLog;
use App\Models\Shop;
use App\Models\User;
use RuntimeException;
use Throwable;

/**
 * Anyone can start: "Google bilan kirish" / "Telegram bilan kirish" signs in
 * the user that account was linked to, or — for an account seen for the
 * first time — asks for the shop's name and phone and opens a new shop on a
 * Shop::TRIAL_DAYS-day trial with this person as its owner. Signed in
 * already (the profile's "connect" buttons): links the account to that user.
 */
class SignupController
{
    public function googleStart(Request $request): void
    {
        if (Desktop::enabled() || !SocialAuth::googleEnabled()) {
            redirect('/login');
        }

        $state = bin2hex(random_bytes(16));
        $_SESSION['google_state'] = $state;
        redirect(SocialAuth::googleAuthUrl($state));
    }

    public function googleCallback(Request $request): void
    {
        $state = (string) ($_SESSION['google_state'] ?? '');
        unset($_SESSION['google_state']);

        if (Desktop::enabled() || !SocialAuth::googleEnabled() || $state === ''
            || !hash_equals($state, (string) $request->input('state', ''))) {
            $this->fail();
        }
        if ((string) $request->input('error', '') !== '') {
            // "Cancel" on Google's page: back without an error message.
            redirect(Auth::check() ? '/profile' : '/login');
        }

        try {
            $identity = SocialAuth::googleExchange((string) $request->input('code', ''));
        } catch (RuntimeException) {
            $this->fail();
        }

        $this->continueWith($identity);
    }

    public function telegramStart(Request $request): void
    {
        if (Desktop::enabled() || !SocialAuth::telegramEnabled()) {
            redirect('/login');
        }

        redirect(SocialAuth::telegramAuthUrl());
    }

    /** Telegram sends the result in the address's #fragment: the page posts it to telegramVerify(). */
    public function telegramCallback(Request $request): void
    {
        View::render('auth/telegram-callback', [], 'layouts/auth');
    }

    public function telegramVerify(Request $request): void
    {
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store');

        $data = json_decode((string) $request->input('result', ''), true);
        $identity = !Desktop::enabled() && is_array($data) ? SocialAuth::telegramVerify($data) : null;
        if ($identity === null) {
            echo json_encode(['ok' => false, 'redirect' => '/login']);
            flash('error', t('social_login_failed'));
            return;
        }

        echo json_encode(['ok' => true, 'redirect' => $this->resolve($identity)]);
    }

    public function form(Request $request): void
    {
        $identity = $_SESSION['pending_identity'] ?? null;
        if (!is_array($identity)) {
            redirect('/login');
        }

        View::render('auth/signup', [
            'identity' => $identity,
            'trialDays' => Shop::TRIAL_DAYS,
        ], 'layouts/auth');
    }

    public function store(Request $request): void
    {
        $identity = $_SESSION['pending_identity'] ?? null;
        if (!is_array($identity)) {
            redirect('/login');
        }

        $shopName = mb_substr(trim((string) $request->input('shop_name', '')), 0, 120);
        $fullName = mb_substr(trim((string) $request->input('full_name', '')), 0, 120);
        $phoneRaw = trim((string) $request->input('phone', ''));
        $old = ['shop_name' => $shopName, 'full_name' => $fullName, 'phone' => $phoneRaw];

        if ($shopName === '' || $fullName === '' || $phoneRaw === '') {
            flash('error', t('fill_required_fields'));
            keep_old($old);
            redirect('/signup');
        }
        $phone = normalize_phone($phoneRaw);
        if ($phone === null || $phone === '') {
            flash('error', t('phone_invalid'));
            keep_old($old);
            redirect('/signup');
        }

        // Signed up already (a second tab, a double click): just sign in.
        $existing = SocialAuth::userIdFor($identity['provider'], $identity['subject']);
        if ($existing !== null) {
            unset($_SESSION['pending_identity']);
            $this->signInAndGo($existing);
        }

        $loginSource = $identity['username'] ?? null;
        if (!$loginSource && !empty($identity['email'])) {
            $loginSource = strstr((string) $identity['email'], '@', true) ?: null;
        }

        $pdo = Database::connect();
        $pdo->beginTransaction();
        try {
            $shopId = Shop::createTrial($shopName, $fullName, $phone);
            $userId = User::create([
                'shop_id' => $shopId,
                'role' => 'owner',
                'full_name' => $fullName,
                'phone' => $phone,
                'login' => SuperAdminController::generateLogin($loginSource ?: $fullName),
                // Nobody knows it: Google / Telegram is the way in until a
                // password is set in the profile (password_set = 0).
                'password' => bin2hex(random_bytes(24)),
                'lang' => current_lang(),
            ]);
            $pdo->prepare('UPDATE users SET password_set = 0 WHERE id = ?')->execute([$userId]);
            SocialAuth::link($identity, $userId);
            $pdo->commit();
        } catch (Throwable $e) {
            $pdo->rollBack();
            log_exception($e);
            flash('error', t('signup_failed'));
            keep_old($old);
            redirect('/signup');
        }

        unset($_SESSION['pending_identity']);
        if (!Auth::attemptUserId($userId)) {
            redirect('/login');
        }
        RememberMe::issue($userId);
        $this->logQuietly($shopId, $userId, $identity['provider']);
        flash('success', t('signup_welcome', ['days' => Shop::TRIAL_DAYS]));
        redirect('/');
    }

    public function subscription(Request $request): void
    {
        $shop = current_shop();
        View::render('subscription', [
            'shop' => $shop,
            'daysLeft' => Shop::daysLeft($shop),
            'expired' => Shop::accessExpired($shop),
        ]);
    }

    /** Where a verified Google / Telegram account goes next (see the class comment). */
    private function resolve(array $identity): string
    {
        if (Auth::check()) {
            $linkedTo = SocialAuth::userIdFor($identity['provider'], $identity['subject']);
            if ($linkedTo !== null && $linkedTo !== Auth::id()) {
                flash('error', t('social_linked_elsewhere'));
            } else {
                SocialAuth::link($identity, (int) Auth::id());
                flash('success', t('social_linked'));
            }

            return '/profile';
        }

        $userId = SocialAuth::userIdFor($identity['provider'], $identity['subject']);
        if ($userId === null) {
            $_SESSION['pending_identity'] = $identity;

            return '/signup';
        }

        if (!Auth::attemptUserId($userId)) {
            flash('error', t('login_failed'));

            return '/login';
        }
        RememberMe::issue($userId);
        $_SESSION['lang'] = Auth::user()['lang'] ?? env('APP_DEFAULT_LANG', 'uz');

        return start_path();
    }

    private function continueWith(array $identity): never
    {
        redirect($this->resolve($identity));
    }

    private function signInAndGo(int $userId): never
    {
        if (Auth::attemptUserId($userId)) {
            RememberMe::issue($userId);
            redirect(start_path());
        }
        redirect('/login');
    }

    private function fail(): never
    {
        flash('error', t('social_login_failed'));
        redirect(Auth::check() ? '/profile' : '/login');
    }

    private function logQuietly(int $shopId, int $userId, string $provider): void
    {
        try {
            ActivityLog::record($shopId, $userId, 'shop_signed_up', ['provider' => $provider]);
        } catch (Throwable) {
            // The log is a nicety; the sign-up itself is done.
        }
    }
}
