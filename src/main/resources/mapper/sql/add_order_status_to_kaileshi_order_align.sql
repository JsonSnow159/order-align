ALTER TABLE `kaileshi_order_align`
    ADD COLUMN `order_status` varchar(64) DEFAULT NULL COMMENT '三方订单状态' AFTER `out_tid`;
