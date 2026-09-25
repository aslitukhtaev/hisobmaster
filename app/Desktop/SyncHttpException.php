<?php

declare(strict_types=1);

namespace App\Desktop;

use RuntimeException;

/** A sync request the server refused or couldn't be reached for ("offline"). */
final class SyncHttpException extends RuntimeException
{
    public function __construct(public readonly string $errorCode)
    {
        parent::__construct($errorCode);
    }
}
