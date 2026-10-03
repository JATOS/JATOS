`legacy-1.4.197.mv.db` is a test database created with H2 1.4.197, user `sa`,
and an empty password. It contains only `guard_test(id INT PRIMARY KEY)` with
one row (`1`). It exercises the startup guard against an actual legacy MVStore
file, including the duplicated format-1 headers. Never open it with a newer
engine to update the fixture.
