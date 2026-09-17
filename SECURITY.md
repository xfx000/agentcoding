# Security Policy

Please report suspected vulnerabilities privately to the repository owner instead of opening a public issue with exploit details.

The built-in `X-Qiqi-User` identity header and H2 data are for local demonstration only. They are not an authentication mechanism. Before deployment:

- replace the demo identity boundary with verified SSO or JWT claims;
- use a dedicated database account that cannot mutate schema or business data;
- keep the table allowlist and data-scope rules explicit;
- store API keys and database passwords in a secret manager;
- add rate limits, request size limits and an audit retention policy;
- verify every new fact table has row-scope bypass tests.
