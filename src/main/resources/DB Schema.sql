CREATE TABLE `file` (
   `id` BIGINT(20) auto_increment PRIMARY KEY,
    `creation_date` DATE,
    `md5` VARCHAR(32)
)

CREATE TABLE `carrier` (
   `id` INT auto_increment PRIMARY KEY,
   `name` VARCHAR(255)
)

CREATE TABLE `file_localization` (
    `id` BIGINT(20) auto_increment PRIMARY KEY,
    `file_id` BIGINT(20),
    `carrier_id` INT,
    `path` TEXT,
    
    FOREIGN KEY (file_id) REFERENCES public.file(id)
    FOREIGN KEY (carrier_id) REFERENCES public.carrier(id)
)