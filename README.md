# Chai
### A beautiful, free, opensource team collaboration for banks and privacy-aware companies
Chai is beautiful and open-source clone of Basecamp. Banks and privacy-aware companies can use Chai for team-collaboration and project management.

This is in no-way meant to replace Basecamp or hurt/steal their business.
Chai's intention to provide a free alternative to people who love Basecamp but can't use it due to security and privacy reasons.
If you are a company that has no such requirements then you should use always use Basecamp as its affordable and amazing.

## Features:
Chai has following features as of today (12 May 2026):
- Company HQ, Teams and Projects
- Messages
- To-dos
- User management (Register, Login, Add/Remove from projects/teams)


The below is the backlog of features planned:

- Email integration (specially for Reset password)
- Notifications
- Automated check-ins
- Direct chat (Pings)
- Other database support
- Documentation, contribution setup etc
- APIs
- Mobile apps (Native or Hybrid)
- Docs & Files
- Chat (May never have as we believe chat is distracting)

## Getting Help & Contributing Back
If interested n in getting any help or contributing back, please write to me on zaheer at hey.com.


## Tech stack
* Java, JSP, JSTL etc
* Spring framework
* Gulp for moving JSP files for hot reloading
* IntelliJ Ultimate
* Turbolinks - https://github.com/turbolinks/turbolinks
* MySQL - https://github.com/turbolinks/turbolinks
* Apache Tomcat
* TailwindCSS and TailwindUI


### Database configuration to support large file uploads
maxAllowedPacket=99999999 in the URL of the database
max_allowed_packet=500M in the my.ini for your mysql server


##### Disclaimer:
I built Chai from ground-up and does not use design or code assets from Basecamp.


#### Environment variables with SQLite Database

```
export chai_spring_port=8080
# export chai_spring_jpa_database_platform=com.github.gwenn.hibernate.dialect.SQLiteDialect

export chai_spring_jpa_database_platform=org.sqlite.hibernate.dialect.SQLiteDialect

# export chai_spring_jpa_database_platform=org.hibernate.dialect.SQLiteDialect

# export chai_spring_datasource_username=default_user

# export chai_spring_datasource_password=default_password

export chai_spring_datasource_url=jdbc:sqlite:database.sqlite

export chai_spring_datasource_driver_class_name=org.sqlite.JDBC

# export chai_spring_jpa_database_platform=org.hibernate.community.dialect.SQLiteDialect

export chai_spring_jpa_database_platform=org.sqlite.hibernate.dialect.SQLiteDialect

# export chai_spring_jpa_database_platform=com.github.gwenn.hibernate.dialect.SQLiteDialect

export chai_spring_email_from=hey@chaihq.com
export chai_spring_mail_host=sandbox.smtp.mailtrap.io
export chai_spring_mail_port=2525
export chai_spring_mail_username=
export chai_spring_mail_password=
export chai_spring_host_url=http://localhost:8080


```