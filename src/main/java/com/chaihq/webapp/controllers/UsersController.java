package com.chaihq.webapp.controllers;


import com.chaihq.webapp.models.ActiveStorageFile;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.Accounts;
import com.chaihq.webapp.services.EmailService;
import com.chaihq.webapp.services.SignIn;
import com.chaihq.webapp.services.UserService;
import com.chaihq.webapp.utilities.Constants;
import com.chaihq.webapp.utilities.Util;
import com.chaihq.webapp.validator.UserValidator;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.tomcat.util.bcel.Const;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;


import java.security.Principal;
import java.security.SecureRandom;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Calendar;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.Authentication;



@Controller
public class UsersController {
    private static final Logger logger = LoggerFactory.getLogger(com.chaihq.webapp.controllers.UsersController.class);

    @Autowired
    ServletContext servletContext;

    @Autowired
    private UserService userService;


    @Autowired
    private UserValidator userValidator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ProjectRepository projectRepository;

    // private SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @Autowired
    private RememberMeServices rememberMeServices;

    private final SecureRandom random = new SecureRandom();
    private static final int TOKEN_BYTE_SIZE = 32;

    private static Logger log = LoggerFactory.getLogger(UsersController.class);

    @Autowired
    private Accounts accounts;

    @Autowired
    private SignIn signIn;

    @RequestMapping(value = "/registration", method = RequestMethod.GET)
    public String registration(Model model) {
        model.addAttribute("user", new User());

        return "registration";
    }

    @RequestMapping(value = "/registration", method = RequestMethod.POST)
    public String registration(@ModelAttribute("user") User userForm, BindingResult bindingResult,
                               @RequestParam(value = "companyName", required = false) String companyName, Model model) {
        userValidator.validate(userForm, bindingResult);
        if (companyName == null || companyName.isBlank()) {
            model.addAttribute("companyNameError", "Enter your company or team name.");
        }
        if (bindingResult.hasErrors() || companyName == null || companyName.isBlank()) {
            model.addAttribute("companyName", companyName);
            return "registration";
        }

        // Signing up again with an existing email just sends that person a login link; it never
        // creates a second user or changes the existing one.
        User user = userRepository.findByEmail(userForm.getEmail());
        if (user == null) {
            user = userForm;
            userService.save(user);
            // Like Basecamp: the company name becomes the new person's account, which they own
            accounts.create(companyName, user);
        }
        sendLoginLink(user);

        model.addAttribute("user", new User());
        model.addAttribute("message", "Check your email for the magic link to login to your account.");
        return "login";
    }

    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public String login(Model model, String error, String logout) {
        if (error != null)
            model.addAttribute("error", "Your username and password is invalid.");

        if (logout != null)
            model.addAttribute("message", "You have been logged out successfully.");

        return "login";
    }

    @PostMapping("/login-magic")
    public String loginMagic(@ModelAttribute("user") User user, Model model) {
        User existingUser = userRepository.findByEmail(user.getEmail());
        model.addAttribute("user", new User());

        if (existingUser == null) {
            model.addAttribute("error", "No account for this email. Sign up first.");
            return "registration";
        }

        sendLoginLink(existingUser);
        model.addAttribute("message", "Check your email for the magic link to login to your account.");
        return "login";
    }

    @GetMapping("/verify-token-and-login")
    public String verifyTokenAndLogin(@RequestParam(value = "token", required = false) String token,
                                      @RequestParam(value = "email", required = false) String email,
                                      Model model, HttpServletRequest request, HttpServletResponse response,
                                      RedirectAttributes redirectAttrs) {
        if (email != null) {
            email = email.replace(" ", "+");
        }
        User existingUser = email == null ? null : userRepository.findByEmail(email);

        if (!isValidLoginToken(existingUser, token)) {
            log.warn("Rejected login link for {}", email);
            model.addAttribute("user", new User());
            model.addAttribute("error", "That login link is invalid or has expired. Enter your email to get a new one.");
            return "login";
        }

        // Links work once
        existingUser.setToken(null);
        existingUser.setTokenUsedDate(Calendar.getInstance());
        existingUser.setTokenExpirationDate(null);
        userRepository.save(existingUser);

        signIn.signIn(existingUser, request, response);
        log.info("User {} signed in with a login link", existingUser.getId());

        redirectAttrs.addFlashAttribute("success", "Login successfull.");
        return "redirect:/projects";
    }

    // Tokens are stored hashed, expire after an hour, and are compared in constant time
    private boolean isValidLoginToken(User user, String token) {
        if (user == null || token == null || user.getToken() == null || user.getTokenExpirationDate() == null) {
            return false;
        }
        if (user.getTokenExpirationDate().before(Calendar.getInstance())) {
            return false;
        }
        return MessageDigest.isEqual(hashToken(token).getBytes(StandardCharsets.UTF_8),
                user.getToken().getBytes(StandardCharsets.UTF_8));
    }

    private void sendLoginLink(User user) {
        String token = generateToken();
        Calendar expirationDate = Calendar.getInstance();
        expirationDate.add(Calendar.HOUR, 1);
        user.setToken(hashToken(token));
        user.setTokenExpirationDate(expirationDate);
        user.setTokenUsedDate(null);
        userRepository.save(user);
        emailService.sendEmail(user, token);
    }

    private static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public String profile(Model model, HttpSession httpSession) {
        User user = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        User userForm = new User();
        userForm.setFirstName(user.getFirstName());
        userForm.setLastName(user.getLastName());
        userForm.setEmail(user.getEmail());
        model.addAttribute("userForm", userForm);
        return "user/profile";
    }


    @RequestMapping(value = "/profile", method = RequestMethod.POST)
    public String update(@ModelAttribute("userForm") User userForm,
                         BindingResult bindingResult, Model model,
                         final RedirectAttributes redirectAttributes,
                         HttpSession httpSession) {

        User currentUser = (User) httpSession.getAttribute(Constants.CURRENT_USER);
        userForm.setEmail(currentUser.getEmail());

        System.out.println("userForm.getEmail: " + userForm.getEmail());
        System.out.println("userForm.getFirstName: " + userForm.getFirstName());
        System.out.println("userForm.getLastName: " + userForm.getLastName());

        userValidator.validateUpdate(userForm, bindingResult);

        System.out.println("bindingResult: " + bindingResult.hasErrors());


        if (bindingResult.hasErrors()) {
            return "user/profile";
        }


        userService.update(userForm);

        // securityService.autologin(userForm.getUsername(), userForm.getPasswordConfirm());

        redirectAttributes.addFlashAttribute("notice", "Your profile saved!");

        return "redirect:/projects";
    }


    @GetMapping("/reset")
    public String reset(Model model) {
        model.addAttribute("userForm", new User());
        return "reset";
    }

    @PostMapping("/reset")
    public String resetPasword(@ModelAttribute("userForm") User userForm, Model model) {
        System.out.println("userForm.getEmail(): " + userForm.getEmail());
        model.addAttribute("message", "Check your inbox for a link to reset your password");
        // TODO: Change this later - For now this just prints the password for the admin who has access to the logs.
        // Admin picks up the password and sends to user. The user is then advised to change their password after
        // the login.
        User user = userService.findByEmail(userForm.getEmail());

        // userToUpdate.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        return "reset";
    }

    /*
    @GetMapping("/avatar/{id}.svg?text={initials}")
    @ResponseBody
    public ResponseEntity<InputStreamResource> avatar(@PathVariable Long id, @PathVariable String initials) {

        String svg = "";

        return ResponseEntity.ok()
                .contentLength(gridFsFile.getLength())
                .contentType(MediaType.parseMediaType(gridFsFile.getContentType()))
                .body(new InputStreamResource(gridFsFile.getInputStream()));
    } */

    /* @GetMapping("/avatar/{userId}/{userInitials}.svg")
    public String getAvatar(@PathVariable Long userId, @PathVariable String userInitials, Model model) {
        model.addAttribute("userId", userId);
        model.addAttribute("userInitials", userInitials);
        return "user/avatars/06D004";
    } */



    @GetMapping("/avatar/{userId}/{userInitials}.svg")
    @ResponseBody
    public ResponseEntity<Resource> serveAvatar(@PathVariable Long userId, @PathVariable String userInitials)
    throws Exception {

        System.out.println("serveAvatar: ");
        Util util = new Util();

        File avatarDir = new File( servletContext.getRealPath("/WEB-INF/jsp/user/avatars/") );
        File avatar = new File(avatarDir.getAbsolutePath() + "/" + util.reduceNumber(userId) + ".svg");
        String avatarString = IOUtils.toString(new FileReader(avatar));
        avatarString = avatarString.replace("_USER_INITIAL_", org.apache.commons.text.StringEscapeUtils.escapeXml11(userInitials));

        InputStream is = IOUtils.toInputStream(avatarString);

        // InputStream is = new FileInputStream(avatarString);

        /* InputStream in = getClass()
                // .getResourceAsStream("/com/chaihq/webapp/avatars/1.svg");
                .getResourceAsStream("/WEB-INF/jsp/user/avatars/1.svg");
        System.out.println("Inputstream: " + in); */
        CacheControl cacheControl = CacheControl.maxAge(60, TimeUnit.SECONDS)
                .noTransform()
                .mustRevalidate();

        return ResponseEntity.ok()
                .cacheControl(cacheControl)
                .contentType(MediaType.parseMediaType("image/svg+xml; charset=utf-8"))
                // .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + activeStorageFile.getFileName() + "\"")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"avatar.svg\"")
                .body(new ByteArrayResource( IOUtils.toByteArray(is) ));

    }


    private void autoLoginAfterSignup(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    // Forms may only set these; id, token, roles, etc. are never taken from a request
    @InitBinder({"user", "userForm"})
    public void userFields(WebDataBinder binder) {
        binder.setAllowedFields("firstName", "lastName", "email");
    }

    private String generateToken() {
        
        byte[] bytes = new byte[TOKEN_BYTE_SIZE];
        random.nextBytes(bytes);
        return String.valueOf(Hex.encode(bytes));
    }


}
