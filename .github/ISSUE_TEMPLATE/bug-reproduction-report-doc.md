#### Description

#### Repository

- **buildType**: [maven|gradle]

Specify how to build the project.

- **projectPath**: project-folder

The path of the project to build inside the repository.

- **versionReference**: master

The branch or tag or commit id to checkout to build the correct version of the application.

#### Reproduction parameters

- **targetFrame**: 1

 The target frame.

- **population**: 100 (Optional)

The size of the population that evolves during the search with a default value of 100.

- **search_budget**: 1800 (Optional)

The search budget in seconds with a default value of 1800.

- **global_timeout**: 1800 (Optional)

The global timeout in seconds, after which the execution stops if the search is stuck with a default value of 1800 (the timeout is only reached if the search does not improve after 1800 seconds).

#### crashLog

The log with the stack trace to consider.
