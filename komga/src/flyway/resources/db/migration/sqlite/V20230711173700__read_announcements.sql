create table ANNOUNCEMENTS_READ
(
    USER_ID         varchar NOT NULL,
    ANNOUNCEMENT_ID varchar NOT NULL,
    PRIMARY KEY (USER_ID, ANNOUNCEMENT_ID),
    FOREIGN KEY (USER_ID) references USER (ID)
);
