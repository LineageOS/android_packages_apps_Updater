/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.updater.model;


public class Change extends ChangelogEntry {

    private String mSubject;
    private String mProject;
    private long mSubmitTimestamp;
    private String mUrl;

    private Change(String subject, String project, long submitTimestamp, String url) {
        mSubject = subject;
        mProject = project;
        mSubmitTimestamp = submitTimestamp;
        mUrl = url;
    }

    public String getSubject() {
        return mSubject;
    }

    public String getProject() {
        return mProject;
    }

    @Override
    public long getTimestamp() {
        return mSubmitTimestamp;
    }

    public String getUrl() {
        return mUrl;
    }

    public static final class Builder {
        private String mSubject;
        private String mProject;
        private Long mSubmitTimestamp = null;
        private String mUrl;

        public Change build() {
            if (mSubject == null || mProject == null || mSubmitTimestamp == null || mUrl == null) {
                throw new IllegalArgumentException("Change object is not complete");
            }
            return new Change(mSubject, mProject, mSubmitTimestamp, mUrl);
        }

        public Builder setUrl(String url) {
            mUrl = url;
            return this;
        }

        public Builder setSubject(String subject) {
            mSubject = subject;
            return this;
        }

        public Builder setProject(String project) {
            mProject = project;
            return this;
        }

        public Builder setTimestamp(long submitTimestamp) {
            mSubmitTimestamp = submitTimestamp;
            return this;
        }
    }
}
