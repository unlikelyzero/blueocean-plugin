package io.jenkins.blueocean.blueocean_bitbucket_pipeline.server;

import hudson.Extension;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.AbstractBitbucketScm;
import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.impl.pipeline.scm.Scm;
import io.jenkins.blueocean.rest.impl.pipeline.scm.ScmFactory;
import org.apache.commons.codec.digest.DigestUtils;

import javax.annotation.Nonnull;

/**
 * @author Vivek Pandey
 */
public class BitbucketServerScm extends AbstractBitbucketScm {
    static final String DOMAIN_NAME="blueocean-bitbucket-server-domain";
    static final String ID = "bitbucket-server";

    public BitbucketServerScm(Reachable parent) {
        super(parent);
    }

    @Nonnull
    @Override
    public String getId() {
        return ID;
    }

    @Nonnull
    @Override
    public String getUri() {
        return getApiUrlParameter();
    }

    @Override
    protected  @Nonnull String createCredentialId(@Nonnull String apiUrl){
        String url = apiUrl;
        if(url.charAt(url.length()-1) != '/'){ //ensure trailing slash
            url = url + "/";
        }
        return String.format("%s:%s",getId(),DigestUtils.sha256Hex(url));
    }

    @Nonnull
    @Override
    protected String getDomainId() {
        return DOMAIN_NAME;
    }

    @Extension
    public static class BbScmFactory extends ScmFactory {
        @Override
        public Scm getScm(String id, Reachable parent) {
            if(id.equals(ID)){
                return getScm(parent);
            }
            return null;
        }

        @Nonnull
        @Override
        public Scm getScm(Reachable parent) {
            return new BitbucketServerScm(parent);
        }
    }
}