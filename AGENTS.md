read all the skills in the SKILLS folder and the codebase
After completing a feature, always check whether it is connected to the permission matrix in both the backend and frontend.
and after that, update status.md and use a table to show that if backend or fe are done, and if they are properly connected
Every feature must enforce permissions from the database-backed permission matrix on both backend and frontend. Do not use role-based fallback as a substitute for permissions. Verify that removing a permission causes the backend API to return 403 and the frontend hides or disables the related menu/action. ROLE_ADMIN is always allowed.
## Permission matrix enforcement
- The database-backed permission matrix is the source of truth for authorization.
- Removing a permission from a role and saving must revoke that permission for users of that role, including existing sessions on their next API request.
- Never grant access through hardcoded roles, default permissions, or role-based fallbacks. Check all layers: security filters, controllers, services, frontend menus, page guards, and action buttons.
- Roles may restrict data scope, but must never bypass a missing permission.
- ROLE_ADMIN always has all permissions. Users with multiple roles receive the union of their roles' permissions.
- Frontend permissions must stay synchronized with the backend; hiding a menu alone is insufficient.
- Verify both cases: granting permission enables the feature; revoking it makes the API return 403 and hides or disables the frontend action.
- Test with a non-admin account and ensure another assigned role is not granting the permission being tested.