-- ── COGNIWARE DATABASE SCHEMA ──
-- Run this in Supabase SQL Editor

-- Enable UUID extension
create extension if not exists "uuid-ossp";

-- ── PROFILES ──
create table public.profiles (
  id uuid references auth.users(id) on delete cascade primary key,
  email text not null,
  name text,
  age integer,
  sex text,
  country text,
  diagnoses text,
  conditions text[],
  medications text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

-- ── ASSESSMENTS ──
create table public.assessments (
  id uuid default uuid_generate_v4() primary key,
  user_id uuid references public.profiles(id) on delete cascade not null,
  created_at timestamptz default now(),
  branch text,
  branch_label text,

  -- Questionnaire scores
  phq9_score integer,
  phq9_answers integer[],
  gad7_score integer,
  gad7_answers integer[],
  sleep_score integer,
  sleep_answers integer[],

  -- Biomarkers
  typing_wpm numeric,
  typing_cv numeric,
  rt_median numeric,
  rt_sd numeric,
  tremor_rms numeric,
  voice jsonb,
  facial jsonb,

  -- Cognitive
  word_recall integer,
  digit_span boolean,
  fluency integer,
  orientation integer,
  trail_correct boolean,

  -- Context
  compare_slider integer,
  social_contact text,
  med_adherence text,
  reflection text,

  -- AI results
  ai_result jsonb,
  crisis_flag boolean default false,

  -- Meta
  duration_minutes integer,
  completed boolean default true
);

-- ── ROW LEVEL SECURITY ──
alter table public.profiles enable row level security;
alter table public.assessments enable row level security;

-- Profiles: users can only read/write their own profile
create policy "Users can view own profile"
  on public.profiles for select
  using (auth.uid() = id);

create policy "Users can insert own profile"
  on public.profiles for insert
  with check (auth.uid() = id);

create policy "Users can update own profile"
  on public.profiles for update
  using (auth.uid() = id);

-- Assessments: users can only read/write their own assessments
create policy "Users can view own assessments"
  on public.assessments for select
  using (auth.uid() = user_id);

create policy "Users can insert own assessments"
  on public.assessments for insert
  with check (auth.uid() = user_id);

-- ── AUTO-UPDATE TIMESTAMP ──
create or replace function public.handle_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

create trigger on_profile_updated
  before update on public.profiles
  for each row execute procedure public.handle_updated_at();

-- ── AUTO-CREATE PROFILE ON SIGNUP ──
create or replace function public.handle_new_user()
returns trigger as $$
begin
  insert into public.profiles (id, email)
  values (new.id, new.email);
  return new;
end;
$$ language plpgsql security definer;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute procedure public.handle_new_user();
