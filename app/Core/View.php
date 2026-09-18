<?php

declare(strict_types=1);

namespace App\Core;

class View
{
    public static function render(string $view, array $data = [], ?string $layout = 'layouts/app'): void
    {
        $data = array_merge(['user' => Auth::user()], $data);
        extract($data, EXTR_SKIP);

        $viewFile = BASE_PATH . '/app/views/' . $view . '.php';
        ob_start();
        require $viewFile;
        $content = ob_get_clean();

        if ($layout !== null) {
            $layoutFile = BASE_PATH . '/app/views/' . $layout . '.php';
            require $layoutFile;
            return;
        }

        echo $content;
    }
}
