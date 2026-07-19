# Evidence: DDD quality red-light slice

- Time: 2026-06-26 16:55:19 Asia/Shanghai
- Environment: local real-pre workspace
- Scope: #29 DDD / quality governance red-light slice
- Branch: local current branch
- Remote deploy: not requested

## Phenomenon

GitHub issue #29 recorded stale quality red lights:

- Frontend Vitest had 2 failing cases in the issue snapshot.
- Backend `mvn test` failed in the current workspace with 5 failures before this slice.

## Evidence Before Fix

Backend full test command before the fix:

```powershell
mvn -q -f backend/pom.xml test
```

Observed failures:

- `DddPolicyLayerNoSpringDependencyTest`: product policy layer imported Spring utilities.
- `DddUserAuthDataScopePolicyBoundaryTest`: boundary test still expected the old `AuthService` seam after the auth logic had moved to `AuthApplication`.
- `TestDouyinGatewayTest`: test gateway product status fixture no longer matched expected page/status filters.
- `TestMockActivityProductSupportTest`: mock product status generation expected rank-based active products at ranks 12 and 18.

Frontend verification command:

```powershell
npm --prefix frontend run test -- --run
```

Result:

- 87 test files passed.
- 657 tests passed.

## Fix Summary

- Removed Spring utility imports from product domain policy classes by using the existing `DomainText` helper.
- Updated the auth DDD boundary test to verify `AuthApplication` owns data-scope role policy delegation while `AuthService` remains a legacy delegate shell.
- Adjusted test Douyin mock product status generation so active status appears on the expected rank cadence used by gateway pagination/filter tests.
- Added a rank 18 status assertion to lock the test fixture contract.

## Verification

Targeted backend command:

```powershell
mvn -q -f backend/pom.xml "-Dtest=DddPolicyLayerNoSpringDependencyTest,DddUserAuthDataScopePolicyBoundaryTest,TestDouyinGatewayTest,TestMockActivityProductSupportTest" test
```

Result: PASS.

Full backend command:

```powershell
mvn -q -f backend/pom.xml test
```

Surefire aggregate after fix:

- tests: 2616
- failures: 0
- errors: 0
- skipped: 3
- suites: 472

Diff hygiene:

```powershell
git diff --check
```

Result: PASS.

## Boundary And Risk

- Production business behavior is not changed by the test mock fixture update.
- Product domain policy classes become more framework-independent.
- No database migration or historical data change is involved.
- #29 remains an aggregate governance issue; this report only closes the currently observed quality red-light slice.

## Conclusion

PASS for this verified red-light slice. #29 overall status remains PARTIAL until the remaining governance scope is separately decomposed and verified.
