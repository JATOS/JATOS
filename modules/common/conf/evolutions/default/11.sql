# --- Add 'endRedirectUrl' column to Study table

# --- !Ups
ALTER TABLE `Study` ADD COLUMN  `endRedirectUrl` varchar(1023) DEFAULT NULL;


# --- !Downs
# --- not supported
