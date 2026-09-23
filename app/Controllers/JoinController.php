<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Auth;
use App\Core\Request;
use App\Core\View;
use App\Models\EmployeeInvite;
use App\Models\Shop;
use App\Models\User;

class JoinController
{
    public function show(Request $request, string $token): void
    {
        $invite = EmployeeInvite::findValidByToken($token);

        if (!$invite) {
            View::render('join/invalid', [], 'layouts/auth');
            return;
        }

        $shop = Shop::find((int) $invite['shop_id']);

        View::render('join/register', ['shop' => $shop, 'token' => $token], 'layouts/auth');
    }

    public function register(Request $request, string $token): void
    {
        $invite = EmployeeInvite::findValidByToken($token);

        if (!$invite) {
            View::render('join/invalid', [], 'layouts/auth');
            return;
        }

        $fullName = trim((string) $request->input('full_name', ''));
        $phone = trim((string) $request->input('phone', ''));
        $login = trim((string) $request->input('login', ''));
        $password = (string) $request->input('password', '');
        $confirmPassword = (string) $request->input('confirm_password', '');

        $old = ['full_name' => $fullName, 'phone' => $phone, 'login' => $login];

        if ($fullName === '' || $login === '' || $password === '') {
            flash('error', t('fill_required_fields'));
            keep_old($old);
            redirect("/join/{$token}");
        }

        $normalizedPhone = normalize_phone($phone);
        if ($normalizedPhone === null) {
            flash('error', t('phone_invalid'));
            keep_old($old);
            redirect("/join/{$token}");
        }
        $phone = $normalizedPhone;

        if (strlen($password) < 6) {
            flash('error', t('password_too_short'));
            keep_old($old);
            redirect("/join/{$token}");
        }

        if ($password !== $confirmPassword) {
            flash('error', t('passwords_not_match'));
            keep_old($old);
            redirect("/join/{$token}");
        }

        if (User::loginExists($login)) {
            flash('error', t('login_taken'));
            keep_old($old);
            redirect("/join/{$token}");
        }

        $userId = EmployeeInvite::claimAndRegister($token, [
            'full_name' => $fullName,
            'phone' => $phone !== '' ? $phone : null,
            'login' => $login,
            'password' => $password,
            'lang' => current_lang(),
        ]);

        if ($userId === null) {
            // Someone else claimed this invite (or it expired) between the check above and now.
            View::render('join/invalid', [], 'layouts/auth');
            return;
        }

        Auth::attempt($login, $password);

        flash('success', t('employee_registered'));
        redirect('/');
    }
}
