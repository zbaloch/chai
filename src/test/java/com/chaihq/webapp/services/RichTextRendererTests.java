package com.chaihq.webapp.services;

import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RichTextRendererTests {

    @Test
    void highlightsMentionsOutsideLinksAndCode() {
        User ayesha = new User();
        ayesha.setId(1);
        ayesha.setFirstName("Ayesha");
        ayesha.setLastName("Khan");
        UserRepository users = mock(UserRepository.class);
        when(users.findAllById(any())).thenReturn(List.of(ayesha));

        String html = new RichTextRenderer(users).render(
                "<p>Thanks @Ayesha Khan &amp; <a href=\"/x\">@Ayesha</a> <code>@Ayesha</code> <script>x</script></p>", "1");

        assertEquals(1, html.split("class=\"mention\"", -1).length - 1, html);
        assertTrue(html.contains("<span class=\"mention\">@Ayesha Khan</span>"), html);
        assertTrue(html.contains("&amp;"), html);
        assertFalse(html.contains("<script"), html);
    }

    @Test
    void noMentionsLeavesHtmlAlone() {
        RichTextRenderer renderer = new RichTextRenderer(mock(UserRepository.class));
        assertEquals(renderer.render("<p>Hi @Ayesha</p>"), renderer.render("<p>Hi @Ayesha</p>", null));
    }
}
