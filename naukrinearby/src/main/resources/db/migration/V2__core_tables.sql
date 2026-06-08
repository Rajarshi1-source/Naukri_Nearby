-- Core schema (master plan §8.2). Embedding dimension (1024) MUST match naukri.embedding.dims.

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(15) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL,                     -- CANDIDATE, EMPLOYER
    name VARCHAR(200),
    email VARCHAR(255),
    is_verified BOOLEAN DEFAULT FALSE,
    preferred_language VARCHAR(5) DEFAULT 'en',
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_user_phone ON users(phone);

CREATE TABLE employers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(300) NOT NULL,
    company_type VARCHAR(50),
    gst_number VARCHAR(20),
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    location GEOGRAPHY(POINT, 4326),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_employer_location ON employers USING GIST(location);

CREATE TABLE candidate_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200),
    phone VARCHAR(15),
    email VARCHAR(255),
    skills TEXT[] NOT NULL DEFAULT '{}',
    experience JSONB DEFAULT '[]',
    education JSONB DEFAULT '[]',
    city VARCHAR(100),
    state VARCHAR(100),
    location GEOGRAPHY(POINT, 4326),
    languages_spoken TEXT[] DEFAULT '{en}',
    total_experience_months INT DEFAULT 0,
    preferred_radius_km INT DEFAULT 10,
    preferred_categories TEXT[] DEFAULT '{}',
    skill_embedding vector(1024),
    resume_file_key VARCHAR(500),
    resume_parsed_at TIMESTAMP,
    profile_completeness INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_candidate_location ON candidate_profiles USING GIST(location);
CREATE INDEX idx_candidate_skills ON candidate_profiles USING GIN(skills);
CREATE INDEX idx_candidate_embedding ON candidate_profiles
    USING hnsw(skill_embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    employer_id BIGINT REFERENCES employers(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    slug VARCHAR(350) UNIQUE NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    skills_required TEXT[] DEFAULT '{}',
    salary_min INT,
    salary_max INT,
    salary_type VARCHAR(20) DEFAULT 'MONTHLY',
    experience_required_months INT DEFAULT 0,
    location GEOGRAPHY(POINT, 4326),
    address TEXT,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    pincode VARCHAR(10),
    radius_km INT DEFAULT 10,
    requirement_vector vector(1024),
    status VARCHAR(20) DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 1,                -- optimistic concurrency / outbox guard
    is_whatsapp_enabled BOOLEAN DEFAULT TRUE,
    application_count INT DEFAULT 0,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_job_location ON jobs USING GIST(location);
CREATE INDEX idx_job_status ON jobs(status);
CREATE INDEX idx_job_city ON jobs(city);
CREATE INDEX idx_job_embedding ON jobs
    USING hnsw(requirement_vector vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES jobs(id) ON DELETE CASCADE,
    candidate_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'APPLIED',
    cover_note TEXT,
    applied_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(job_id, candidate_id)
);
CREATE INDEX idx_app_job ON applications(job_id);
CREATE INDEX idx_app_candidate ON applications(candidate_id);

CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(20) DEFAULT 'WHATSAPP',
    language VARCHAR(5) DEFAULT 'en',
    radius_km INT DEFAULT 10,
    categories TEXT[] DEFAULT '{}',
    frequency VARCHAR(20) DEFAULT 'INSTANT',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE notification_logs (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT REFERENCES users(id),
    job_id BIGINT REFERENCES jobs(id),
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    message_sid VARCHAR(100),
    translated_text TEXT,
    language VARCHAR(5),
    sent_at TIMESTAMP DEFAULT NOW(),
    delivered_at TIMESTAMP,
    read_at TIMESTAMP,
    UNIQUE(candidate_id, job_id)                   -- idempotency backstop (§9.4)
);
CREATE INDEX idx_notif_candidate ON notification_logs(candidate_id);
CREATE INDEX idx_notif_status ON notification_logs(status);
