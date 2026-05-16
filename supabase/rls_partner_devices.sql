-- Fix: partner can read shared device records
-- Run this once in the Supabase SQL Editor.
--
-- The default "owner only" policy blocks User B from reading User A's device rows.
-- This policy adds a SELECT-only exception: any user in a partnership may read the
-- device records that are shared within that partnership.
create policy "partner can read shared devices"
  on devices for select
  using (
    id::text in (
      select sd.device_id
      from shared_devices sd
      join partnerships p on sd.partnership_id = p.id
      where p.uid_a = auth.uid()::text
         or p.uid_b = auth.uid()::text
    )
  );
