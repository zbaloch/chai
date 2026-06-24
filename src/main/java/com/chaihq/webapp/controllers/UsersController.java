package com.chaihq.webapp.controllers;


import com.chaihq.webapp.models.ActiveStorageFile;
import com.chaihq.webapp.models.Project;
import com.chaihq.webapp.models.User;
import com.chaihq.webapp.repositories.ProjectRepository;
import com.chaihq.webapp.repositories.UserRepository;
import com.chaihq.webapp.services.EmailService;
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

    @RequestMapping(value = "/registration", method = RequestMethod.GET)
    public String registration(Model model) {
        model.addAttribute("user", new User());

        return "registration";
    }

    @RequestMapping(value = "/registration", method = RequestMethod.POST)
    public String registration(@ModelAttribute("user") User userForm, 
            BindingResult bindingResult, 
            Model model, 
            HttpServletRequest request,
            HttpServletResponse response
        ) {
        userValidator.validate(userForm, bindingResult);

        if (bindingResult.hasErrors()) {
            return "registration";
        }

        User existingUser = userRepository.findByEmail(userForm.getEmail());
        String token = generateToken();

        if(existingUser != null) {
            
            existingUser.setToken(token);
            Calendar expirationDate = Calendar.getInstance();
            expirationDate.add(Calendar.HOUR, 1);
            existingUser.setTokenExpirationDate( expirationDate );
            existingUser.setTokenUsedDate(null);
            userRepository.save(existingUser);
            emailService.sendEmail(existingUser);

        } else {

            userForm.setToken(token);
            Calendar expirationDate = Calendar.getInstance();
            expirationDate.add(Calendar.HOUR, 1);
            userForm.setTokenExpirationDate( expirationDate );
            userForm.setTokenUsedDate(null);
            userRepository.save(userForm);
            emailService.sendEmail(userForm);

        }

        log.info("token " + token);
        model.addAttribute("user", new User());
        
        model.addAttribute("message", "Check your email for the magic link to login to your account.");

        userService.save(userForm);

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
    public String loginMagic(User user, 
        Model model,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        User existingUser = userRepository.findByEmail(user.getEmail());

        String token = generateToken();

        if(existingUser != null) {
            
            existingUser.setToken(token);
            Calendar expirationDate = Calendar.getInstance();
            expirationDate.add(Calendar.HOUR, 1);
            existingUser.setTokenExpirationDate( expirationDate );
            existingUser.setTokenUsedDate(null);
            userRepository.save(existingUser);

            log.info("token " + token);
            model.addAttribute("user", new User());

            emailService.sendEmail(existingUser);

            model.addAttribute("message", "Check your email for the magic link to login to your account.");
            
            return "login";

        } else {

            log.info("token " + token);
            model.addAttribute("user", new User());

            model.addAttribute("error", "No account for this email. Sign up first.");

           return "registration";

        }
        
    }

    @GetMapping("/verify-token-and-login")
    public String verifyTokenAndLogin(User user, 
        Model model,
        @Param(value = "token") String token,
        @Param(value = "email") String email,
        HttpServletRequest request,
        HttpServletResponse response,
        RedirectAttributes redirectAttrs
    ) {
        if(email.contains(" ")) {
            email = email.replaceAll(" ", "+");
        }
        log.info(email);
        log.info(token);
        User existingUser = userRepository.findByEmailAndToken(email, token);
        if(existingUser != null) {

             SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            final Authentication authentication = new UsernamePasswordAuthenticationToken(
                existingUser.getEmail(), null, java.util.Collections.emptyList());
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            rememberMeServices.loginSuccess(request, response, authentication);

            // Add currentUser to session
            request.getSession().setAttribute("currentUser", existingUser);

            redirectAttrs.addFlashAttribute("success", "Login successfull.");

            // Invalidate the token
            existingUser.setToken(null);
            existingUser.setTokenUsedDate(null);
            existingUser.setTokenExpirationDate(null);
            userRepository.save(existingUser);

            model.addAttribute("user", existingUser);

            return "redirect:/projects";
        } else {
            model.addAttribute("error", "No account for this email. Signup now to selling on Async.");

           return "login";
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
        avatarString = avatarString.replace("_USER_INITIAL_", userInitials);

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
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+ userInitials + ".svg\"")
                .body(new ByteArrayResource( IOUtils.toByteArray(is) ));

    }


    private void autoLoginAfterSignup(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private String generateToken() {
        
        byte[] bytes = new byte[TOKEN_BYTE_SIZE];
        random.nextBytes(bytes);
        return String.valueOf(Hex.encode(bytes));
    }


}
