package io.jenkins.blueocean.blueocean_bitbucket_pipeline.server;


import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.util.Secret;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.BitbucketApi;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.BitbucketApiFactory;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.model.BbBranch;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.model.BbPage;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.model.BbProject;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.model.BbRepo;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.model.BbSaveContentResponse;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.model.BbUser;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.server.model.BbServerBranch;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.server.model.BbServerPage;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.server.model.BbServerProject;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.server.model.BbServerRepo;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.server.model.BbServerSaveContentResponse;
import io.jenkins.blueocean.blueocean_bitbucket_pipeline.server.model.BbServerUser;
import io.jenkins.blueocean.commons.ServiceException;
import org.antlr.v4.runtime.misc.NotNull;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.jenkins.blueocean.commons.JsonConverter.om;

/**
 * @author Vivek Pandey
 */
public class BitbucketServerApi extends BitbucketApi {
    private final String baseUrl;
    private final String basicAuthHeaderValue;
    private final String userName;

    //package private for testing
    BitbucketServerApi(@Nonnull String hostUrl, @Nonnull StandardUsernamePasswordCredentials credentials) {
        super(hostUrl, credentials);
        this.baseUrl=ensureTrailingSlash(hostUrl)+"rest/api/1.0/";
        try {
            this.basicAuthHeaderValue = String.format("Basic %s",
                    Base64.encodeBase64String(String.format("%s:%s", credentials.getUsername(),
                            Secret.toString(credentials.getPassword())).getBytes("UTF-8")));
            this.userName = credentials.getUsername();
        } catch (UnsupportedEncodingException e) {
            throw new ServiceException.UnexpectedErrorException("Failed to create basic auth header: "+e.getMessage(), e);
        }
    }

    @Override
    public @Nonnull
    BbUser getUser(){
        return getUser(this.userName);
    }

    @Override
    public @Nonnull BbUser getUser(@Nonnull String userName){
        try {
            InputStream inputStream = Request.Get(String.format("%s/%s",baseUrl+"users", userName))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, BbServerUser.class);
        } catch (IOException e) {
            throw handleException(e);
        }
    }


    @Override
    public @Nonnull
    BbPage<BbProject> getProjects(int start, int limit){
        try {
            InputStream inputStream = Request.Get(String.format("%s?start=%s&limit=%s",baseUrl+"projects/", start,limit))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, new TypeReference<BbServerPage<BbServerProject>>(){});
        } catch (IOException e) {
            throw handleException(e);
        }
    }

    @Override
    public @Nonnull BbProject getProject(@Nonnull String projectName){
        try {
            InputStream inputStream = Request.Get(String.format("%s/%s",baseUrl+"projects", projectName))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, BbServerProject.class);
        } catch (IOException e) {
            throw handleException(e);
        }
    }


    @Override
    public @Nonnull
    BbPage<BbRepo> getRepos(@Nonnull String projectKey, int pageNumber, int pageSize){
        try {
            if(pageNumber <= 0){
                pageNumber = 1;
            }
            if(pageSize <= 0){
                pageSize = 500; //bitbucket server default
            }
            int start = pageSize*(pageNumber-1);
            InputStream inputStream = Request.Get(String.format("%s?start=%s&limit=%s",baseUrl+"projects/"+projectKey+"/repos/", start,pageSize))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, new TypeReference<BbServerPage<BbServerRepo>>(){});
        } catch (IOException e) {
            throw handleException(e);
        }
    }

    @Nonnull
    @Override
    public BbRepo getRepo(@Nonnull String projectKey, @Nonnull String repoSlug) {
        try {
            InputStream inputStream = Request.Get(String.format("%s/%s/repos/%s/",baseUrl+"projects",projectKey, repoSlug))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, BbServerRepo.class);
        } catch (IOException e) {
            throw handleException(e);
        }
    }

    @Override
    public @Nonnull String getContent(@Nonnull String projectKey, @Nonnull String repoSlug, @Nonnull String path, @Nonnull String commitId){
        List<String> content = new ArrayList<>();
        getAndBuildContent(projectKey, repoSlug, path, commitId,0, 500, content); //default size as in bitbucket API
        return Joiner.on('\n').join(content);
    }

    @Override
    public @Nonnull
    BbSaveContentResponse saveContent(@Nonnull String projectKey,
                                      @Nonnull String repoSlug,
                                      @Nonnull String path,
                                      @Nonnull String content,
                                      @Nonnull String commitMessage,
                                      @Nonnull String branch,
                                      @Nullable String commitId){
        try {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                    .addTextBody("content", content)
                    .addTextBody("message", commitMessage)
                    .addTextBody("branch", branch);

            if(org.apache.commons.lang.StringUtils.isNotBlank(commitId)){
                   builder.addTextBody("sourceCommitId", commitId);
            }
            HttpEntity entity = builder.build();
            InputStream inputStream = Request.Put(String.format("%s/%s/repos/%s/browse/%s",baseUrl+"projects",projectKey,repoSlug, path))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .body(entity)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, BbServerSaveContentResponse.class);
        } catch (IOException e) {
            throw handleException(e);
        }
    }

    @Override
    public boolean fileExists(String projectKey, String repoSlug, String path, String branch){
        try {
            URIBuilder uriBuilder = new URIBuilder(String.format("%s/%s/repos/%s/browse/%s",baseUrl+"projects",
                    projectKey, repoSlug, path));

            if(branch != null){
                uriBuilder.addParameter("at", "refs/heads/"+branch);
            }
            Response response = Request.Head(uriBuilder.build())
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute();
            return response.returnResponse().getStatusLine().getStatusCode() == 200;
        } catch (IOException | URISyntaxException e) {
            throw handleException(e);
        }
    }

    @Override
    public @CheckForNull
    BbBranch getBranch(@Nonnull String projectKey, @Nonnull String repoSlug, @Nonnull String branch){
        try {
            URIBuilder uriBuilder = new URIBuilder(String.format("%s/%s/repos/%s/branches/",baseUrl+"projects",
                    projectKey, repoSlug));

            uriBuilder.addParameter("filterText", branch);
            BbServerPage<BbServerBranch> branches = om.readValue(Request.Get(uriBuilder.build())
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream(), new TypeReference<BbServerPage<BbServerBranch>>() {
            });
            String expectedId = "refs/heads/"+branch;
            for(BbServerBranch b : branches.getValues()){
                if(b.getId().equals(expectedId)){
                    return b;
                }
            }
            return null;
        } catch (IOException | URISyntaxException e) {
            throw handleException(e);
        }
    }

    @Override
    @Nonnull
    public BbBranch createBranch(@Nonnull String projectKey, @Nonnull String repoSlug, Map<String, String> payload){
        try {
            return om.readValue(Request.Post(String.format("%s/%s/repos/%s/branches/", baseUrl + "projects",
                    projectKey, repoSlug))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .bodyString(om.writeValueAsString(payload), ContentType.APPLICATION_JSON)
                    .execute().returnContent().asStream(), BbServerBranch.class);
        }catch (IOException e){
            throw handleException(e);
        }
    }

    @Override
    public @CheckForNull
    BbBranch getDefaultBranch(@Nonnull String projectKey, @Nonnull String repoSlug){
        try {
            InputStream inputStream = Request.Get(String.format("%s/%s/repos/%s/branches/default",baseUrl+"projects",
                    projectKey, repoSlug))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();
            return om.readValue(inputStream, new TypeReference<BbServerBranch>() {
            });
        }catch (HttpResponseException e) {
            if(e.getStatusCode() == 404){ //empty repo gives 404, we ignore these
                return null;
            }
            throw handleException(e);
        }catch (IOException e) {
            throw handleException(e);
        }
    }

    @Override
    public boolean isEmptyRepo(@NotNull String projectKey, @Nonnull String repoSlug){
        try {
            URIBuilder uriBuilder = new URIBuilder(String.format("%s/%s/repos/%s/branches/default",baseUrl+"projects",
                    projectKey, repoSlug));

            Response response = Request.Head(uriBuilder.build())
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute();
            return response.returnResponse().getStatusLine().getStatusCode() == 404;
        } catch (IOException | URISyntaxException e) {
            throw handleException(e);
        }
    }

    private String ensureTrailingSlash(String url){
        if(url.charAt(url.length() - 1) != '/'){
            return url+"/";
        }
        return url;
    }

    private void getAndBuildContent(@Nonnull String projectKey, @Nonnull String repoSlug, @Nonnull String path, @Nonnull String commitId, int start, int limit, @Nonnull final List<String> lines){
        try {
            InputStream inputStream = Request.Get(String.format("%s/%s/repos/%s/browse/%s?at=%s&start=%s&limit=%s",baseUrl+"projects",
                    projectKey, repoSlug, path, commitId, start, limit))
                    .addHeader("Authorization", basicAuthHeaderValue)
                    .execute().returnContent().asStream();

            Map<String,Object> content = om.readValue(inputStream, new TypeReference<Map<String,Object>>(){});
            List<Map<String, String>> lineMap = (List<Map<String, String>>) content.get("lines");
            collectLines(lineMap, lines);
            int size = (int) content.get("size");
            if(!(boolean)content.get("isLastPage")){
                getAndBuildContent(projectKey, repoSlug, path, commitId, start+size, limit, lines);
            }
        } catch (IOException e) {
            throw handleException(e);
        }
    }

    private void collectLines(List<Map<String,String>> lineMap, final List<String> lines){
        lines.addAll(Lists.transform(lineMap, new Function<Map<String,String>, String>() {
            @Nullable
            @Override
            public String apply(@Nullable Map<String, String> input) {
                if(input != null){
                    String text = input.get("text");
                    if(text != null){
                        return text;
                    }
                }
                return null;
            }
        }));
    }

    private ServiceException handleException(Exception e){
        if(e instanceof HttpResponseException){
            return new ServiceException(((HttpResponseException) e).getStatusCode(), e.getMessage(), e);
        }
        return new ServiceException.UnexpectedErrorException(e.getMessage(), e);
    }

    @Extension
    public static class BitbucketServerApiFactory extends BitbucketApiFactory{
        @Override
        public boolean handles(@Nonnull String apiUrl) {
            //If its not cloud apiUrl then it's server
            return !apiUrl.startsWith("https://bitbucket.org/");
        }

        @Nonnull
        @Override
        public BitbucketApi newInstance(@Nonnull String apiUrl, @Nonnull StandardUsernamePasswordCredentials credentials) {
            return new BitbucketServerApi(apiUrl, credentials);
        }

    }
}
