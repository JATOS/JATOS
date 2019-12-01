# --- Add 'linear' column to Study table

# --- !Ups
ALTER TABLE `Study` ADD COLUMN  `linearStudy` tinyint(1) NOT NULL;


# --- !Downs
# --- not supported
