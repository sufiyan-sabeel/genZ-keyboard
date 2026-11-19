# Migration structure

## Why this directory does not mirror the remote history

The live project records **three** migrations. This repository contains **one** forward migration.
That is not an accident, and no local file corresponds to any remote migration — verified by
comparing the stored statements against the files:

| Remote version | Name | Stored SQL | Local file match |
|---|---|---|---|
| `20261118161726` | `genz_keyboard_community_core` | 24361 B, md5 `03a8085…` | **none** |
| `20261118161910` | `genz_keyboard_community_idempotent` | 20806 B, md5 `8043334…` | **none** |
| `20261118161946` | `genz_keyboard_hardening` | 22608 B, md5 `2a9a583…` | **none** |
| *(none)* | storage buckets + policies | applied out of band | `0002_storage.sql`, 2309 B |

The three remote versions were **three runs of the same evolving schema** during initial deployment:
apply, fix duplicated views, then harden. The repository's snapshot (`supabase/baseline/`) is the
end state, which is 23595 B — matching none of them, because it is their cumulative result.

## Layout

```
supabase/
├── schema.sql                      canonical current schema (same content as the baseline)
├── baseline/
│   └── 0001_community_core.sql     snapshot for fresh deploys — NOT a migration
└── migrations/
    ├── README.md                   this file
    └── 0002_storage.sql            the one migration applied out of band
```

The snapshot lives in `baseline/`, not `migrations/`, because the Supabase CLI treats every file in
`migrations/` as a pending migration and would try to push it onto a project that already has it.

## Rules for future work

1. **Do not run `supabase db push` against the existing project.** Its history is already complete;
   a push would attempt `0002_storage.sql` again. That file is idempotent, so the damage would be a
   duplicate version row — not data loss — but it is still wrong.
2. **New changes go in `migrations/`** as `<UTC timestamp>_<description>.sql`, applied once, in
   order. Never edit an applied migration.
3. **Fresh environments** are built from `baseline/` + `migrations/`, in that order.
4. **Never** rewrite, delete, or `migration repair` the remote history to make it match this
   directory. The remote rows record what actually ran.
5. `0002_storage.sql` is applied out of band because the migration runner cannot resolve the
   `storage` schema (`relation "storage" does not exist`) even though it exists and is reachable from
   plain SQL. Until that is resolved, storage changes must be applied manually and committed here.

## Known gap

`0002_storage.sql` has no row in the remote migration history. Reconciling that would require either
a CLI `migration repair` (deliberately not done — it would be marking a migration as applied without
evidence) or re-running it through the runner (which fails on the storage schema). The gap is
documented rather than papered over.
