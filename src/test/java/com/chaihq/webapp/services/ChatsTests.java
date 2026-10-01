package com.chaihq.webapp.services;

import com.chaihq.webapp.models.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChatsTests {

    private static User user(long id, String first, String last) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        return user;
    }

    private final User ayesha = user(1, "Ayesha", "Khan");
    private final User jin = user(2, "Jin", "Lee");
    private final User jinPark = user(3, "Jin", "Park");

    @Test
    void mentionsByFullName() {
        assertEquals(Set.of(1L), Chats.mentionedIds("Thanks @Ayesha Khan!", List.of(ayesha, jin)));
    }

    @Test
    void mentionsByFirstNameOnlyWhenItIsUnique() {
        assertEquals(Set.of(1L), Chats.mentionedIds("@ayesha can you look?", List.of(ayesha, jin, jinPark)));
        assertEquals(Set.of(), Chats.mentionedIds("@Jin can you look?", List.of(ayesha, jin, jinPark)));
        assertEquals(Set.of(3L), Chats.mentionedIds("@Jin Park can you look?", List.of(ayesha, jin, jinPark)));
    }

    @Test
    void emailAddressesAreNotMentions() {
        assertEquals(Set.of(), Chats.mentionedIds("write to ayesha@Ayesha.com", List.of(ayesha)));
        assertEquals(Set.of(), Chats.mentionedIds("@Ayeshas", List.of(ayesha)));
    }

    @Test
    void htmlIsEscapedWithLinksMentionsAndLineBreaks() {
        String html = Chats.html("<b>hi</b> @Ayesha Khan\nsee https://example.com/a?b=1&c=2.", List.of(ayesha));
        assertTrue(html.startsWith("&lt;b&gt;hi&lt;/b&gt; <span"), html);
        assertTrue(html.contains(">@Ayesha Khan</span>"), html);
        assertTrue(html.contains("<br>"), html);
        assertTrue(html.contains("href=\"https://example.com/a?b=1&amp;c=2\""), html);
        assertTrue(html.endsWith("</a>."), html);
    }

    @Test
    void quotesCannotBreakOutOfALink() {
        String html = Chats.html("https://x.com/\"onmouseover=alert(1)", List.of());
        assertFalse(html.contains("\"onmouseover"), html);
    }
}
