<?php

declare(strict_types=1);

namespace App\Controllers;

use App\Core\Request;

class TelegramController
{
    public function webhook(Request $request): void
    {
        $secret = (string) env('TELEGRAM_WEBHOOK_SECRET', '');
        $incomingSecret = (string) ($_SERVER['HTTP_X_TELEGRAM_BOT_API_SECRET_TOKEN'] ?? '');

        // TELEGRAM_WEBHOOK_SECRET sozlangan bo'lsa (bo'sh bo'lmasa), Telegram yuborgan
        // sarlavha undan farq qilishi mumkin emas — hash_equals() bilan doimiy vaqtda solishtiramiz.
        // $secret hech qachon bo'sh qatorga solishtirilmaydi (chap tomon uchun ham), shuning uchun
        // bu yerda "bo'sh == bo'sh" degan yolg'on moslik yuzaga kelmaydi.
        // Sozlanmagan bo'lsa (bo'sh), tekshiruv o'tkazib yuboriladi — bu README'da ko'rsatilgan,
        // qabul qilingan xavf (bot integratsiyasi ixtiyoriy).
        if ($secret !== '' && !hash_equals($secret, $incomingSecret)) {
            http_response_code(403);
            return;
        }

        $update = json_decode((string) file_get_contents('php://input'), true);
        $message = $update['message'] ?? null;
        $chatId = $message['chat']['id'] ?? null;
        $text = trim((string) ($message['text'] ?? ''));

        if ($chatId !== null && str_starts_with($text, '/start')) {
            $this->sendWebAppButton((int) $chatId);
        }

        http_response_code(200);
    }

    private function sendWebAppButton(int $chatId): void
    {
        $token = (string) env('TELEGRAM_BOT_TOKEN', '');
        $appUrl = rtrim((string) env('APP_URL', ''), '/');

        if ($token === '' || $appUrl === '') {
            return;
        }

        $this->callTelegramApi($token, 'sendMessage', [
            'chat_id' => $chatId,
            'text' => t('telegram_welcome_message'),
            'reply_markup' => json_encode([
                'inline_keyboard' => [[
                    ['text' => t('telegram_open_app_button'), 'web_app' => ['url' => $appUrl]],
                ]],
            ]),
        ]);
    }

    private function callTelegramApi(string $token, string $method, array $payload): void
    {
        $ch = curl_init("https://api.telegram.org/bot{$token}/{$method}");
        curl_setopt_array($ch, [
            CURLOPT_POST => true,
            CURLOPT_POSTFIELDS => http_build_query($payload),
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT => 10,
        ]);
        curl_exec($ch);
        curl_close($ch);
    }
}
