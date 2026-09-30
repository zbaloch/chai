# Chai
### A beautiful, free, self-hostable team collaboration for banks and privacy-aware companies
Chai is beautiful and source-available clone of Basecamp. Banks and privacy-aware companies can use Chai for team-collaboration and project management.

This is in no-way meant to replace Basecamp or hurt/steal their business.
Chai's intention to provide a free alternative to people who love Basecamp but can't use it due to security and privacy reasons.
If you are a company that has no such requirements then you should use always use Basecamp as its affordable and amazing.

## Features:
Chai has following features as of today (12 May 2026):
- Projects
- Messages
- To-dos
- User management (Register, Login, Add/Remove from projects/teams)
- Email integration (specially for Reset password)
- Notifications

The below is the backlog of features planned:

- Project chat
- Direct chat (Pings)
- Documentation, contribution setup etc
- APIs
- Mobile apps (Native or Hybrid)
- Docs & Files
- Chat (May never have as we believe chat is distracting)
- Search across todos, messages, comments, people, chat and even notifications.
- All messages, all todos in one place
- Automated check-ins
- Other databases support (postgresql, oracle, sql server)

## License
Free to use, modify and self-host — just not to sell as a competing hosted service. Each release becomes MIT after two years. [FSL-1.1-MIT](LICENSE) · [Details](LICENSING.md)

## Getting Help & Contributing Back
If interested n in getting any help or contributing back, please write to me on zaheer at hey.com.


## Tech stack
* Java, JSP, JSTL etc
* Spring framework
* Gulp for moving JSP files for hot reloading
* IntelliJ Ultimate
* Visual Studio Code
* Turbolinks - https://github.com/turbolinks/turbolinks
* MySQL - https://github.com/turbolinks/turbolinks
* Apache Tomcat
* TailwindCSS and TailwindUI


### Database configuration to support large file uploads
maxAllowedPacket=99999999 in the URL of the database
max_allowed_packet=500M in the my.ini for your mysql server


##### Disclaimer:
I built Chai from ground-up and does not use design or code assets from Basecamp.
