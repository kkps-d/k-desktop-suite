# Ground Rules

1. Be concise in explanations and implementation. Avoid unnecessary code.
2. Write human-friendly code. Keep abstractions necessary and avoid creating utilities or functions used only once unless they improve clarity. Use proper whitespace and let the code explain itself. Add short comments where the code may be confusing or hard to read.
3. Follow each platform's conventions, including idiomatic APIs, naming, project structure, and UI behavior.
4. Keep changes focused. Only change what the task requires. Avoid unrelated refactoring.
5. Keep platform boundaries clear. Share platform-independent logic; keep platform-specific behavior in the appropriate module.
6. Verify meaningful changes. Run relevant builds or tests and state what couldn't be verified.
7. Follow [Design guidelines](DESIGN_GUIDELINES.md) for UI layout, platform styling, typography, and note-taking interactions.
8. Follow Conventional Commits, add the related feature in brackets if needed
9. Never run tests on connected ADB remote devices, only test on local emulators. Start the local emulator if it isn't running, use "Small Desktop" to run your tests

## Documentation and verification

- Read [Development](DEVELOPMENT.md) for setup, build commands, and module ownership.
- Read [Testing](TESTING.md) for verification commands and local Small Desktop
  emulator requirements. Report what could not be verified.
- Keep the root README focused on the apps, getting started, and user-visible
  limitations. Put detailed usage, implementation rules, and verification notes
  under `docs/`, and link them from the [documentation index](README.md).
- Update the relevant app guide when behavior changes. Specifications describe
  implementation requirements and planned behavior; distinguish those from
  delivered features.
