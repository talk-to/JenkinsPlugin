package com.flock.plugin;

import hudson.model.AbstractBuild;
import hudson.model.Result;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Mockito.*;

public class TestFlockNotifier {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    final String correctWebHookUrl = "https://google.com";
    final String wrongWebHookUrl = "https://google.com";

    final FlockNotifier correctFlockNotifier = new FlockNotifier(correctWebHookUrl, true,true,true,true,true,true,true,true);
    final FlockNotifier incorrectFlockNotifier = new FlockNotifier(wrongWebHookUrl, true,true,true,true,true,true,true,true);

    @Test
    public void testCorrectWebhook() {
        assert(correctFlockNotifier.isNotifyOnSuccess() == true);
        assert(correctFlockNotifier.isNotifyOnFailure() == true);
        assert(correctFlockNotifier.isNotifyOnAborted() == true);
        assert(correctFlockNotifier.isNotifyOnUnstable() == true);
        assert(correctFlockNotifier.isNotifyOnNotBuilt() == true);
        assert(correctFlockNotifier.isNotifyOnBackToNormal() == true);
        assert(correctFlockNotifier.isNotifyOnRegression() == true);
    }

    @Test
    public void testIncorrectWebhook() {
        assert(correctFlockNotifier.isNotifyOnSuccess() == true);
        assert(correctFlockNotifier.isNotifyOnFailure() == true);
        assert(correctFlockNotifier.isNotifyOnAborted() == true);
        assert(correctFlockNotifier.isNotifyOnUnstable() == true);
        assert(correctFlockNotifier.isNotifyOnNotBuilt() == true);
        assert(correctFlockNotifier.isNotifyOnBackToNormal() == true);
        assert(correctFlockNotifier.isNotifyOnRegression() == true);
    }

    @Test
    public void startedTest() {
        AbstractBuild build = mock(AbstractBuild.class);
        when(build.getResult()).thenReturn(Result.FAILURE);
        assert(build.getResult() == Result.FAILURE);
    }

}