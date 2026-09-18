<?php

declare(strict_types=1);

namespace App\Core;

class Router
{
    private array $routes = [];
    private array $middlewareMap = [];

    public function setMiddlewareMap(array $map): void
    {
        $this->middlewareMap = $map;
    }

    public function get(string $path, array|callable $handler, array $middleware = []): void
    {
        $this->add('GET', $path, $handler, $middleware);
    }

    public function post(string $path, array|callable $handler, array $middleware = []): void
    {
        $this->add('POST', $path, $handler, $middleware);
    }

    private function add(string $method, string $path, array|callable $handler, array $middleware): void
    {
        $this->routes[] = compact('method', 'path', 'handler', 'middleware');
    }

    public function dispatch(Request $request): void
    {
        $method = $request->method();
        $uri = $request->path();

        foreach ($this->routes as $route) {
            if ($route['method'] !== $method) {
                continue;
            }

            $pattern = preg_replace('#\{([a-zA-Z_][a-zA-Z0-9_]*)\}#', '(?P<$1>[^/]+)', $route['path']);
            $pattern = '#^' . $pattern . '$#u';

            if (!preg_match($pattern, $uri, $matches)) {
                continue;
            }

            $params = array_filter($matches, static fn ($key) => is_string($key), ARRAY_FILTER_USE_KEY);

            foreach ($route['middleware'] as $middleware) {
                [$name, $arg] = array_pad(explode(':', $middleware, 2), 2, null);
                $class = $this->middlewareMap[$name] ?? null;
                if ($class === null) {
                    continue;
                }

                $redirect = $arg !== null ? $class::handle($request, $arg) : $class::handle($request);
                if ($redirect !== null) {
                    redirect($redirect);
                    return;
                }
            }

            $this->callHandler($route['handler'], $request, $params);
            return;
        }

        http_response_code(404);
        View::render('errors/404', [], 'layouts/auth');
    }

    private function callHandler(array|callable $handler, Request $request, array $params): void
    {
        if (is_array($handler)) {
            [$class, $method] = $handler;
            $controller = new $class();
            $controller->$method($request, ...array_values($params));
            return;
        }

        $handler($request, ...array_values($params));
    }
}
