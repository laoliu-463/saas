# DDD100 #70 Talent Address Boundary Evidence

## Scope
- Issue: #70 `[DDD100-TALENT-ADDRESS] 达人地址供寄样域消费边界`
- Environment: local `real-pre`
- Code commit: `af359240`
- Remote deploy: no

## Changes
- Added `TalentShippingAddressDTO` as the explicit talent-domain address fact for sample workflows.
- Added `TalentAddressApplicationService`.
- Added `TalentDomainFacade.findClaimShippingAddress(channelUserId, talentId)`.
- Implemented claim-address read in `LegacyTalentDomainFacade`.
- Routed `TalentController` shipping-address GET/PUT through `TalentAddressApplicationService`.
- Extended architecture guard to prevent direct controller fallback to `TalentService` address methods.

## Compatibility Boundary
- No API path, response shape, request DTO, DB schema, default real-pre config, or permission pre-check was intentionally changed.
- Address write still delegates to `TalentService.updateShippingAddress`, preserving the current active-claim owner rule.
- Sample creation already wrote back through `TalentDomainFacade.writeBackClaimAddress`; this slice adds the read-side address fact boundary.
- No real-pre data was modified by the validation commands beyond backend container rebuild/restart.

## Verification
```powershell
mvn -q -f backend/pom.xml "-Dtest=TalentControllerTest,TalentAddressApplicationServiceTest,LegacyTalentDomainFacadeTest" test
mvn -q -f backend/pom.xml "-Dtest=TalentControllerTest,TalentAddressApplicationServiceTest,LegacyTalentDomainFacadeTest,DddTalentProfileApplicationRoutingTest,TalentServiceTest,TalentAddressPolicyTest,SampleControllerTest,SampleLifecycleServiceTest" test
powershell -NoProfile -ExecutionPolicy Bypass -File .\harness\scripts\commands\agent-do.ps1 -Env real-pre -Scope backend -ContentMaintenance off -Message "feat: route talent address application"
```

## Results
- Red test: first targeted run failed because `TalentAddressApplicationService` and `TalentShippingAddressDTO` did not exist.
- Targeted address tests: PASS.
- Combined #70 tests: PASS.
- agent-do backend: PASS.
- Backend build: PASS, `mvn -f backend/pom.xml -DskipTests package`.
- Docker restart: PASS, `backend-real-pre` rebuilt/recreated.
- Health check: PASS, `GET /api/system/health` returned `{"status":"UP"}`.
- Business validation: PASS, `npm run e2e:real-pre:p0:preflight`.

## Reports
- Gate evidence: `harness/reports/evidence-20260627-162941.md`.
- Retro: `harness/reports/retro-20260627-163005.md`.
- real-pre preflight output: `runtime/qa/out/real-pre-preflight-20260627-162939`.

## Residual Risk
- No authenticated real-pre positive HTTP sample was claimed for a user who owns an address-bearing active claim.
- Third-party talent gateway evidence remains #71; broader talent E2E and permission negatives remain #72.

## Conclusion
PASS for #70. Talent address read/write now has an explicit talent-domain Application/Facade boundary for sample consumption without moving sample rules into the talent domain.
