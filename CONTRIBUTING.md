# Contributing

Use JDK 21 and Maven 3.9 or newer. Run `mvn test` before opening a pull request.

For changes to SQL execution, add tests for both the intended query and at least one bypass attempt. Do not weaken a rejection rule solely to make a model-generated query pass. Fix the query or add a narrowly defined business rule with evidence.

Do not commit API keys, real customer data, private course material, generated credentials or local configuration files.
