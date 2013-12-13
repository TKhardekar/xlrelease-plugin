/**
 * Copyright (c) 2013, XebiaLabs B.V., All rights reserved.
 *
 *
 * The Deployit plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

package com.xebialabs.xlrelease.ci;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import com.google.common.base.Strings;

import com.xebialabs.xlrelease.ci.server.XLReleaseServer;
import com.xebialabs.xlrelease.ci.server.XLReleaseServerFactory;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static hudson.util.FormValidation.error;
import static hudson.util.FormValidation.ok;
import static hudson.util.FormValidation.warning;

public class XLReleaseNotifier extends Notifier {

    public final String credential;

    public final String application;
    public final String version;

    public final boolean verbose;


    @DataBoundConstructor
    public XLReleaseNotifier(String credential, String application, String version, boolean verbose) {
        this.credential = credential;
        this.application = application;
        this.version = version;
        this.verbose = verbose;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {

        return true;
    }

    private XLReleaseServer getDeployitServer() {
        return getDescriptor().getDeployitServer(credential);
    }

    @Override
    public DeployitDescriptor getDescriptor() {
        return (DeployitDescriptor) super.getDescriptor();
    }

    @Extension
    public static final class DeployitDescriptor extends BuildStepDescriptor<Publisher> {

        // ************ SERIALIZED GLOBAL PROPERTIES *********** //

        private String xlReleaseServerUrl;

        private String xlReleaseClientProxyUrl;

        private List<Credential> credentials = newArrayList();

        // ************ OTHER NON-SERIALIZABLE PROPERTIES *********** //

        private final transient Map<String,XLReleaseServer> credentialServerMap = newHashMap();

        public DeployitDescriptor() {
            load();  //deserialize from xml
            mapCredentialsByName();
        }

        private void mapCredentialsByName() {
            for (Credential credential : credentials) {
                String serverUrl = credential.resolveServerUrl(xlReleaseServerUrl);
                String proxyUrl = credential.resolveProxyUrl(xlReleaseClientProxyUrl);

                credentialServerMap.put(credential.name,
                        XLReleaseServerFactory.newInstance(serverUrl, proxyUrl, credential.username, credential.password.getPlainText()));
            }
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            //this method is called when the global form is submitted.
            xlReleaseServerUrl = json.get("xlReleaseServerUrl").toString();
            xlReleaseClientProxyUrl = json.get("xlReleaseClientProxyUrl").toString();
            credentials = req.bindJSONToList(Credential.class, json.get("credentials"));
            save();  //serialize to xml
            mapCredentialsByName();
            return true;
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "";
        }

        private FormValidation validateOptionalUrl(String url) {
            try {
                if (!Strings.isNullOrEmpty(url)) {
                    new URL(url);
                }
            } catch (MalformedURLException e) {
                return error("%s is not a valid URL.",url);
            }
            return ok();

        }

        public FormValidation doCheckDeployitServerUrl(@QueryParameter String deployitServerUrl) {
            if (Strings.isNullOrEmpty(deployitServerUrl)) {
                return error("Url required.");
            }
            return validateOptionalUrl(deployitServerUrl);
        }

        public FormValidation doCheckDeployitClientProxyUrl(@QueryParameter String deployitClientProxyUrl) {
            return validateOptionalUrl(deployitClientProxyUrl);
        }


        public List<Credential> getCredentials() {
            return credentials;
        }

        public String getXlReleaseServerUrl() {
            return xlReleaseServerUrl;
        }

        public String getXlReleaseClientProxyUrl() {
            return xlReleaseClientProxyUrl;
        }

        public ListBoxModel doFillCredentialItems() {
            ListBoxModel m = new ListBoxModel();
            for (Credential c : credentials)
                m.add(c.name, c.name);
            return m;
        }

        public FormValidation doCheckCredential(@QueryParameter String credential) {
            return warning("Changing credentials may unintentionally change your deployables' types - check the definitions afterward");
        }

        @Override
        public Publisher newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return super.newInstance(req, formData);
        }





        private XLReleaseServer getDeployitServer(String credential) {
            checkNotNull(credential);
            return credentialServerMap.get(credential);
        }


        private Credential getDefaultCredential() {
            if (credentials.isEmpty())
                throw new RuntimeException("No credentials defined in the system configuration");
            return credentials.iterator().next();
        }
    }
}
