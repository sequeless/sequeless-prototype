# SequelessUi

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.0.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

The Playwright suite lives in `e2e/` and is opt-in: it is not part of `verify` or CI. Install nothing at run time;
the browsers must already be present (Playwright never downloads them from this config). Start the stack, then run:

```bash
npm run e2e
```

Set these to point the suite at a running stack:

- `E2E_BASE_URL`: default `http://localhost:4200` (the `ng serve` proxy). Use `http://localhost:8080` for the fat jar.
- `E2E_OBJECT_TYPE` (default `Project`), `E2E_SIGNAL_TYPE` (default `Project`), `E2E_SIGNAL_NAME` (default `reopen`).

`E2E_SKIP_BROWSER_CHECK=1` skips the browser check; use it only for `npx playwright test --list`.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
