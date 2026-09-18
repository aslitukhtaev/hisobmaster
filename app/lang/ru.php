<?php

declare(strict_types=1);

return [
    'app_name' => 'HisobMaster',
    'tagline' => 'Система учёта для магазина',

    // Auth
    'login' => 'Логин',
    'password' => 'Пароль',
    'login_button' => 'Войти',
    'login_required' => 'Введите логин и пароль',
    'login_failed' => 'Неверный логин или пароль',
    'csrf_error' => 'Сессия истекла, попробуйте снова',
    'logout' => 'Выйти',
    'welcome' => 'Добро пожаловать, :name',

    // Roles
    'role_super_admin' => 'Супер админ',
    'role_owner' => 'Владелец магазина',
    'role_employee' => 'Сотрудник',

    // Nav / dashboard
    'dashboard' => 'Главная',
    'products' => 'Товары',
    'sales' => 'Продажи',
    'customers' => 'Должники',
    'expenses' => 'Расходы',
    'reports' => 'Отчёты',
    'employees' => 'Сотрудники',
    'profile' => 'Профиль',
    'shops' => 'Магазины',
    'coming_soon' => 'Скоро',
    'system_running' => 'Система запущена',

    // Errors
    'error_404_title' => 'Страница не найдена',
    'error_404_text' => 'Запрашиваемая страница не существует.',
    'error_403_title' => 'Нет доступа',
    'error_403_text' => 'У вас нет прав для доступа к этому разделу.',
    'back_to_home' => 'Вернуться на главную',

    // Shops / super admin
    'total_shops' => 'Всего магазинов',
    'active_shops' => 'Активные магазины',
    'blocked_shops' => 'Заблокированные',
    'recent_shops' => 'Недавно добавленные магазины',
    'view_all' => 'Смотреть все',
    'no_shops_yet' => 'Пока не добавлено ни одного магазина.',
    'shops_count_label' => 'магазин(ов)',
    'shop_name' => 'Название магазина',
    'owner_full_name' => 'Владелец (Ф.И.О)',
    'phone' => 'Телефон',
    'address' => 'Адрес',
    'status' => 'Статус',
    'created_at_label' => 'Дата создания',
    'actions' => 'Действия',
    'active_status' => 'Активен',
    'blocked_status' => 'Заблокирован',
    'create_shop' => 'Новый магазин',
    'create_shop_hint' => 'Введите данные магазина — логин и пароль будут созданы автоматически.',
    'optional' => 'необязательно',
    'reset_password' => 'Сбросить пароль',
    'toggle_block' => 'Заблокировать',
    'toggle_activate' => 'Активировать',
    'confirm_reset_password' => 'Создать новый пароль для этого магазина? Старый пароль перестанет работать.',
    'confirm_block' => 'Заблокировать магазин? Владелец и сотрудники не смогут войти в систему.',
    'confirm_activate' => 'Снова активировать магазин?',
    'shop_status_updated' => 'Статус магазина обновлён',
    'owner_not_found' => 'Владелец магазина не найден',
    'new_shop_credentials' => 'Данные для входа',
    'credentials_warning' => 'Эти данные показываются только один раз — передайте их владельцу магазина. Пароль можно изменить позже в профиле.',
    'back_to_list' => 'Вернуться к списку',
    'fill_required_fields' => 'Заполните все обязательные поля',

    // Profile
    'full_name_label' => 'Ф.И.О',
    'change_password_optional' => 'Смена пароля (необязательно)',
    'current_password' => 'Текущий пароль',
    'new_password' => 'Новый пароль',
    'confirm_password' => 'Подтвердите новый пароль',
    'save' => 'Сохранить',
    'login_taken' => 'Этот логин занят, выберите другой',
    'current_password_wrong' => 'Текущий пароль неверен',
    'password_too_short' => 'Новый пароль должен содержать минимум 6 символов',
    'passwords_not_match' => 'Новые пароли не совпадают',
    'profile_updated' => 'Профиль успешно обновлён',
];
