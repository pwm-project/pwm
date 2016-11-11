## Set up

### Build
Run the following commands
1. `npm install`
2. `npm run build`

### Setup Development Environment
Run the following commands
1. `npm install`
2. `npm start`
3. `npm test` (optional)

### Useful commands
* `npm run build` Starts a production build
* `npm start` Starts the development environment, which watches your 
system for any file changes and rebuilds automatically. It also serves
the dist/ folder from http://localhost:4000/
* `npm run clean` Cleans dist/ directory
* `npm test` Starts test environment, which will watch your system for 
any file changes, rebuild your code, and run unit tests
* `npm run test-single-run` Builds the code and runs all unit tests a
one time

## Known Issues

### Visual Studio Code
* Jasmine global properties not recognized in Typescript. https://github.com/Microsoft/TypeScript/issues/11620