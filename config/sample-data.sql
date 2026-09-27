INSERT INTO public.customer
(
    id,
    name,
    phone,
    email,
    gstin,
    address,
    dob,
    gender,
    credit_limit,
    loyalty_points,
    whatsapp_opt_in,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted
)
VALUES
(
    2,
    'ABI',
    '09791919500',
    'rajeshkumar.veeru@gmail.com',
    '1244',
    '1/122-B, South s',
    NULL,
    NULL,
    0.00,
    0.00,
    true,
    1,
    '2026-08-25 04:06:28.228431',
    1,
    '2026-08-25 04:06:28.228431',
    false
);

INSERT INTO public.customer
(
    id,
    name,
    phone,
    email,
    gstin,
    address,
    dob,
    gender,
    credit_limit,
    loyalty_points,
    whatsapp_opt_in,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted
)
VALUES
(
    4,
    'RAJESHKUMAR P',
    '9791919500',
    'rajeshkumar.veeru@gmail.ss',
    '',
    '1/122-B, South street,Seplanatham south,Seplanatham post,Virudhachalam TK',
    NULL,
    NULL,
    0.00,
    0.00,
    true,
    1,
    '2026-08-29 18:00:26.442603',
    1,
    '2026-08-29 18:00:26.442603',
    false
);

INSERT INTO public.customer
(
    id,
    name,
    phone,
    email,
    gstin,
    address,
    dob,
    gender,
    credit_limit,
    loyalty_points,
    whatsapp_opt_in,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted
)
VALUES
(
    1,
    'Rajesh',
    '7456789345',
    'rajeshkumar.p@gmail.com',
    '',
    'adree',
    NULL,
    NULL,
    0.00,
    0.00,
    true,
    1,
    '2026-08-24 14:39:10.343579',
    1,
    '2026-08-29 18:01:10.977504',
    false
);

INSERT INTO public.customer
(
    id,
    name,
    phone,
    email,
    gstin,
    address,
    dob,
    gender,
    credit_limit,
    loyalty_points,
    whatsapp_opt_in,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted
)
VALUES
(
    3,
    'SAM',
    '6879845348',
    'sam.veeru@mobile.com',
    '',
    '1/122-B, South street,Seplanatham south,Seplanatham post,Virudhachalam TK',
    NULL,
    NULL,
    0.00,
    0.00,
    true,
    1,
    '2026-08-29 15:13:48.283343',
    1,
    '2026-08-29 18:01:31.720746',
    false
);

INSERT INTO public.customer
(
    id,
    name,
    phone,
    email,
    gstin,
    address,
    dob,
    gender,
    credit_limit,
    loyalty_points,
    whatsapp_opt_in,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted
)
VALUES
(
    5,
    'RAJESHKUMAR P',
    '9791919500',
    '',
    '',
    '1/122-B, South street,Seplanatham south,Seplanatham post,Virudhachalam TK',
    NULL,
    NULL,
    0.00,
    0.00,
    true,
    1,
    '2026-09-01 06:58:50.054072',
    1,
    '2026-09-01 06:58:50.054072',
    false
);

INSERT INTO public.customer
(
    id,
    name,
    phone,
    email,
    gstin,
    address,
    dob,
    gender,
    credit_limit,
    loyalty_points,
    whatsapp_opt_in,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted
)
VALUES
(
    6,
    'hri',
    '9791919500',
    '',
    '',
    '1/122-B, South street,Seplanatham south,Seplanatham post,Virudhachalam TK',
    NULL,
    NULL,
    0.00,
    0.00,
    true,
    1,
    '2026-09-01 07:13:19.872804',
    1,
    '2026-09-01 07:13:19.872804',
    false
);



--
-- TOC entry 5133 (class 0 OID 20794)
-- Dependencies: 230
-- Data for Name: product; Type: TABLE DATA; Schema: public; Owner: -
--
INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    8,
    'BL001',
    'Building Block',
    'Toy',
    '100',
    300.00,
    449.00,
    18.00,
    98.000,
    0.000,
    true,
    1,
    '2026-09-04 10:11:03.960132',
    1,
    '2026-09-04 11:06:21.20393',
    false,
    '1003211332'
);


INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    10,
    'DT001',
    'Surf',
    'Soap',
    'Pcs',
    5.00,
    10.00,
    10.00,
    98.000,
    0.000,
    true,
    1,
    '2026-09-04 11:14:36.395433',
    1,
    '2026-09-04 11:17:41.581937',
    false,
    '8901030872976'
);


INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    11,
    'DT002',
    'Exo',
    'Soap',
    'Pcs',
    5.00,
    10.00,
    10.00,
    99.000,
    0.000,
    true,
    1,
    '2026-09-04 11:15:46.57281',
    1,
    '2026-09-04 11:17:41.621713',
    false,
    '8902102165415'
);


INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    3,
    'FR-002',
    'Desk',
    'Funiture',
    '245',
    100.00,
    200.00,
    10.00,
    0.000,
    2.000,
    false,
    1,
    '2026-08-25 04:07:50.22669',
    1,
    '2026-08-26 17:43:02.706488',
    false,
    NULL
);


INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    2,
    'FR-001',
    'Chair',
    'Funiture',
    '10',
    100.00,
    200.00,
    12.00,
    0.000,
    12.000,
    false,
    1,
    '2026-08-24 16:06:49.350603',
    1,
    '2026-08-27 04:43:39.076152',
    false,
    NULL
);


INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    4,
    'FR-003',
    'Cuboard',
    'Funiture',
    'PCS',
    10000.00,
    12000.00,
    10.00,
    3.000,
    5.000,
    false,
    1,
    '2026-08-26 16:49:34.548176',
    1,
    '2026-09-02 20:52:52.475167',
    false,
    NULL
);


INSERT INTO public.product
(
    id,
    sku,
    name,
    category,
    unit,
    purchase_price,
    sale_price,
    gst_percent,
    stock_qty,
    reorder_level,
    active,
    created_by,
    created_at,
    updated_by,
    updated_at,
    deleted,
    barcode
)
VALUES
(
    7,
    'Ph-001',
    'IPHONE',
    '7',
    'PCS',
    10000.00,
    12000.00,
    18.00,
    3.000,
    56.000,
    false,
    1,
    '2026-08-29 15:16:33.551004',
    1,
    '2026-09-02 20:53:32.053281',
    false,
    NULL
);

--
-- TOC entry 5137 (class 0 OID 20925)
-- Dependencies: 238
-- Data for Name: purchase_item; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- TOC entry 5139 (class 0 OID 20983)
-- Dependencies: 242
-- Data for Name: sale; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.sale VALUES (6, 'INV-1787584906205', 1, NULL, '2026-08-24 00:00:00', 0.00, 0.00, 0.00, 0.00, 'DRAFT', 1, '2026-08-24 15:22:42.942782', 1, '2026-08-24 17:47:56.820791', true);
INSERT INTO public.sale VALUES (1, 'INV-1787583173540', 1, NULL, '2026-08-24 00:00:00', 12.00, 32.00, 0.00, 3.00, 'DRAFT', 1, '2026-08-24 14:53:05.563565', 1, '2026-08-24 17:48:11.064649', true);
INSERT INTO public.sale VALUES (2, 'INV-1787583823668', 1, NULL, '2026-08-24 00:00:00', 32.00, 23.00, 0.00, 23.00, 'DRAFT', 1, '2026-08-24 15:04:11.243057', 1, '2026-08-24 17:48:13.116865', true);
INSERT INTO public.sale VALUES (3, 'INV-1787584020877', 1, NULL, '2026-08-24 00:00:00', 22.00, 22.00, 0.00, 22.00, 'DRAFT', 1, '2026-08-24 15:09:36.139944', 1, '2026-08-24 17:48:14.980516', true);
INSERT INTO public.sale VALUES (4, 'INV-1787584176153', 1, NULL, '2026-08-24 00:00:00', 0.00, 0.00, 0.00, 0.00, 'DRAFT', 1, '2026-08-24 15:09:43.174857', 1, '2026-08-24 17:48:16.627829', true);
INSERT INTO public.sale VALUES (5, 'INV-1787584513462', 1, NULL, '2026-08-24 00:00:00', 0.00, 0.00, 0.00, 0.00, 'DRAFT', 1, '2026-08-24 15:15:53.559226', 1, '2026-08-24 17:48:18.718128', true);
INSERT INTO public.sale VALUES (7, 'INV-1787584962966', 1, NULL, '2026-08-24 00:00:00', 0.00, 0.00, 0.00, 0.00, 'DRAFT', 1, '2026-08-24 15:25:41.106137', 1, '2026-08-24 17:48:20.717779', true);
INSERT INTO public.sale VALUES (8, 'INV-1787587469544', 1, NULL, '2026-08-24 00:00:00', 1500.00, 270.00, 0.00, 1770.00, 'COMPLETED', 1, '2026-08-24 16:05:50.782401', 1, '2026-08-24 17:48:22.471708', true);
INSERT INTO public.sale VALUES (9, 'INV-1787587612621', 1, NULL, '2026-08-24 00:00:00', 3900.00, 558.00, 0.00, 4458.00, 'COMPLETED', 1, '2026-08-24 16:07:16.32306', 1, '2026-08-24 17:48:24.467814', true);
INSERT INTO public.sale VALUES (10, 'INV-1787587687850', 1, 1, '2026-08-24 00:00:00', 200.00, 24.00, 0.00, 224.00, 'COMPLETED', 1, '2026-08-24 16:08:22.060904', 1, '2026-08-24 17:48:26.301531', true);
INSERT INTO public.sale VALUES (11, 'INV-1787589607622', 1, NULL, '2026-08-24 00:00:00', 125.00, 22.50, 10.00, 137.50, 'COMPLETED', 1, '2026-08-24 16:44:30.706277', 1, '2026-08-24 17:48:28.865244', true);
INSERT INTO public.sale VALUES (13, 'INV-1787633409541', 1, NULL, '2026-08-25 00:00:00', 325.00, 42.50, 10.00, 357.50, 'COMPLETED', 1, '2026-08-25 04:50:35.53951', 1, '2026-08-25 04:55:19.422809', true);
INSERT INTO public.sale VALUES (12, 'INV-1787591713360', 1, NULL, '2026-08-24 00:00:00', 525.00, 70.50, 100.00, 495.50, 'COMPLETED', 1, '2026-08-24 17:16:16.487328', 1, '2026-08-25 04:55:21.822796', true);
INSERT INTO public.sale VALUES (15, 'INV-1787634671466', 1, NULL, '2026-08-25 00:00:00', 600.00, 64.00, 10.00, 654.00, 'COMPLETED', 1, '2026-08-25 05:11:40.609306', 1, '2026-08-25 05:11:40.609306', false);
INSERT INTO public.sale VALUES (16, 'INV-1787635168699', 1, NULL, '2026-08-25 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-08-25 05:19:45.573395', 1, '2026-08-25 06:29:26.477227', true);
INSERT INTO public.sale VALUES (17, 'INV-1787639398890', 1, NULL, '2026-08-25 00:00:00', 600.00, 68.00, 1.00, 667.00, 'COMPLETED', 1, '2026-08-25 06:30:19.03526', 1, '2026-08-25 06:47:11.920536', false);
INSERT INTO public.sale VALUES (18, 'INV-1787726193400', 1, NULL, '2026-08-26 00:00:00', 400.00, 48.00, 5.00, 443.00, 'COMPLETED', 1, '2026-08-26 06:37:06.446642', 1, '2026-08-26 06:37:06.446642', false);
INSERT INTO public.sale VALUES (19, 'INV-1787726564525', 1, NULL, '2026-08-26 00:00:00', 400.00, 40.00, 10.00, 430.00, 'COMPLETED', 1, '2026-08-26 06:43:01.744165', 1, '2026-08-26 06:43:14.939455', false);
INSERT INTO public.sale VALUES (20, 'INV-1787728079389', 1, NULL, '2026-08-26 00:00:00', 250.00, 45.00, 10.00, 285.00, 'COMPLETED', 1, '2026-08-26 07:08:07.594966', 1, '2026-08-26 07:08:38.877341', false);
INSERT INTO public.sale VALUES (51, 'INV-1788021117464', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 16:32:06.00286', 1, '2026-08-29 16:32:06.00286', false);
INSERT INTO public.sale VALUES (23, 'INV-1787729368196', 1, NULL, '2026-08-26 00:00:00', 400.00, 48.00, 0.00, 448.00, 'COMPLETED', 1, '2026-08-26 07:29:37.632538', 1, '2026-08-26 07:43:34.385539', false);
INSERT INTO public.sale VALUES (33, 'INV-1787765728879', 1, NULL, '2026-08-26 00:00:00', 1400.00, 140.00, 0.00, 1540.00, 'COMPLETED', 1, '2026-08-26 17:35:37.581808', 1, '2026-08-26 17:38:56.088301', false);
INSERT INTO public.sale VALUES (36, 'INV-1787765997397', 1, NULL, '2026-08-26 00:00:00', 600.00, 60.00, 0.00, 660.00, 'COMPLETED', 1, '2026-08-26 17:40:06.394181', 1, '2026-08-26 17:40:06.394181', false);
INSERT INTO public.sale VALUES (26, 'INV-1787735562260', 1, NULL, '2026-08-26 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-08-26 09:12:53.52982', 1, '2026-08-26 09:13:04.897893', false);
INSERT INTO public.sale VALUES (45, 'INV-1787931223220', 1, NULL, '2026-08-28 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-08-28 15:34:01.556751', 1, '2026-08-28 15:34:01.556751', false);
INSERT INTO public.sale VALUES (25, 'INV-1787734930912', 1, 1, '2026-08-26 00:00:00', 600.00, 60.00, 0.00, 660.00, 'COMPLETED', 1, '2026-08-26 09:03:48.893204', 1, '2026-08-26 15:13:46.888566', false);
INSERT INTO public.sale VALUES (24, 'INV-1787732509658', 1, 1, '2026-08-26 00:00:00', 1800.00, 216.00, 100.00, 1916.00, 'COMPLETED', 1, '2026-08-26 08:24:20.561052', 1, '2026-08-26 15:14:18.026286', false);
INSERT INTO public.sale VALUES (22, 'INV-1787729005430', 1, NULL, '2026-08-26 00:00:00', 200.00, 24.00, 0.00, 224.00, 'COMPLETED', 1, '2026-08-26 07:28:11.058302', 1, '2026-08-26 15:20:23.429989', false);
INSERT INTO public.sale VALUES (21, 'INV-1787728990190', 1, 1, '2026-08-26 00:00:00', 800.00, 92.00, 10.00, 882.00, 'COMPLETED', 1, '2026-08-26 07:23:25.249648', 1, '2026-08-26 15:28:04.218709', false);
INSERT INTO public.sale VALUES (27, 'INV-1787761596577', 1, NULL, '2026-08-26 00:00:00', 200.00, 24.00, 0.00, 224.00, 'COMPLETED', 1, '2026-08-26 16:27:24.765654', 1, '2026-08-26 16:27:24.765654', false);
INSERT INTO public.sale VALUES (28, 'INV-1787762351033', 1, NULL, '2026-08-26 00:00:00', 200.00, 24.00, 0.00, 224.00, 'COMPLETED', 1, '2026-08-26 16:39:24.280895', 1, '2026-08-26 16:39:24.280895', false);
INSERT INTO public.sale VALUES (29, 'INV-1787762364320', 1, NULL, '2026-08-26 00:00:00', 200.00, 24.00, 0.00, 224.00, 'COMPLETED', 1, '2026-08-26 16:39:40.779219', 1, '2026-08-26 16:39:40.779219', false);
INSERT INTO public.sale VALUES (37, 'INV-1787768783883', 1, NULL, '2026-08-26 00:00:00', 24000.00, 2400.00, 0.00, 26400.00, 'COMPLETED', 1, '2026-08-26 18:26:34.21696', 1, '2026-08-26 18:51:08.201088', false);
INSERT INTO public.sale VALUES (38, 'INV-1787771294597', 1, NULL, '2026-08-27 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-26 19:08:23.273457', 1, '2026-08-26 19:08:23.273457', false);
INSERT INTO public.sale VALUES (46, 'INV-1787938876650', 1, NULL, '2026-08-28 00:00:00', 12250.00, 1245.00, 0.00, 13495.00, 'COMPLETED', 1, '2026-08-28 17:46:50.418232', 1, '2026-08-28 17:46:50.418232', false);
INSERT INTO public.sale VALUES (47, 'INV-1787939210789', 1, 1, '2026-08-28 00:00:00', 12250.00, 1245.00, 0.00, 13495.00, 'COMPLETED', 1, '2026-08-28 17:48:03.560851', 1, '2026-08-28 17:48:03.560851', false);
INSERT INTO public.sale VALUES (39, 'INV-1787775131966', 1, NULL, '2026-08-27 00:00:00', 400.00, 48.00, 0.00, 448.00, 'COMPLETED', 1, '2026-08-26 20:12:47.905342', 1, '2026-08-27 04:43:39.069642', false);
INSERT INTO public.sale VALUES (40, 'INV-1787809904098', 1, NULL, '2026-08-27 00:00:00', 12000.00, 1200.00, 0.00, 13200.00, 'COMPLETED', 1, '2026-08-27 05:52:18.970646', 1, '2026-08-27 05:52:18.970646', false);
INSERT INTO public.sale VALUES (41, 'INV-1787810144583', 1, NULL, '2026-08-27 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-08-27 06:09:09.348922', 1, '2026-08-27 06:09:09.348922', false);
INSERT INTO public.sale VALUES (44, 'INV-1787925718041', 1, NULL, '2026-08-28 00:00:00', 250.00, 45.00, 0.00, 295.00, 'COMPLETED', 1, '2026-08-28 14:02:20.66138', 1, '2026-08-28 14:02:20.66138', false);
INSERT INTO public.sale VALUES (42, 'INV-1787923572163', 1, 1, '2026-08-28 00:00:00', 250.00, 45.00, 0.00, 295.00, 'DRAFT', 1, '2026-08-28 13:30:03.018961', 1, '2026-08-28 18:35:06.117535', false);
INSERT INTO public.sale VALUES (43, 'INV-1787923803250', 1, NULL, '2026-08-28 00:00:00', 250.00, 45.00, 5.00, 290.00, 'DRAFT', 1, '2026-08-28 13:39:34.611159', 1, '2026-08-28 19:14:50.19378', false);
INSERT INTO public.sale VALUES (48, 'INV-1788016675188', 1, 1, '2026-08-29 00:00:00', 12000.00, 2160.00, 60.00, 14100.00, 'DRAFT', 1, '2026-08-29 15:19:19.1849', 1, '2026-08-29 15:19:19.1849', false);
INSERT INTO public.sale VALUES (49, 'INV-1788019787909', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 16:14:49.472078', 1, '2026-08-29 16:14:49.472078', false);
INSERT INTO public.sale VALUES (50, 'INV-1788020782524', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 16:26:34.723364', 1, '2026-08-29 16:26:34.723364', false);
INSERT INTO public.sale VALUES (52, 'INV-1788021198136', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 16:34:24.768018', 1, '2026-08-29 16:34:24.768018', false);
INSERT INTO public.sale VALUES (53, 'INV-1788021841345', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 16:44:16.043302', 1, '2026-08-29 16:44:16.043302', false);
INSERT INTO public.sale VALUES (54, 'INV-1788022719612', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 16:58:49.552681', 1, '2026-08-29 16:58:49.552681', false);
INSERT INTO public.sale VALUES (55, 'INV-1788022851528', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 17:01:11.623365', 1, '2026-08-29 17:01:11.623365', false);
INSERT INTO public.sale VALUES (56, 'INV-1788022928328', 1, NULL, '2026-08-29 00:00:00', 125.00, 22.50, 0.00, 147.50, 'DRAFT', 1, '2026-08-29 17:05:07.954153', 1, '2026-08-29 17:05:07.954153', false);
INSERT INTO public.sale VALUES (57, 'INV-1788023872428', 1, NULL, '2026-08-29 00:00:00', 12125.00, 1222.50, 0.00, 13347.50, 'COMPLETED', 1, '2026-08-29 17:18:13.544081', 1, '2026-08-29 17:18:22.69557', false);
INSERT INTO public.sale VALUES (58, 'INV-1788361897452', 1, NULL, '2026-09-02 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-09-02 15:15:16.608541', 1, '2026-09-02 15:15:16.608541', false);
INSERT INTO public.sale VALUES (60, 'INV-1788373087691', 1, NULL, '2026-09-02 00:00:00', 12125.00, 1222.50, 0.00, 13347.50, 'DRAFT', 1, '2026-09-02 18:26:45.553389', 1, '2026-09-02 18:26:45.553389', false);
INSERT INTO public.sale VALUES (62, 'INV-1788380443291', 1, NULL, '2026-09-03 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-09-02 20:21:06.171443', 1, '2026-09-02 20:21:06.171443', false);
INSERT INTO public.sale VALUES (63, 'INV-1788380624683', 1, NULL, '2026-09-03 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-09-02 20:28:28.642765', 1, '2026-09-02 20:28:28.642765', false);
INSERT INTO public.sale VALUES (64, 'INV-1788382289223', 1, NULL, '2026-09-03 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-09-02 20:51:34.079216', 1, '2026-09-02 20:51:34.079216', false);
INSERT INTO public.sale VALUES (65, 'INV-1788382294240', 1, NULL, '2026-09-03 00:00:00', 12000.00, 1200.00, 0.00, 13200.00, 'COMPLETED', 1, '2026-09-02 20:52:19.653105', 1, '2026-09-02 20:52:19.653105', false);
INSERT INTO public.sale VALUES (66, 'INV-1788382339735', 1, NULL, '2026-09-03 00:00:00', 12000.00, 1200.00, 0.00, 13200.00, 'COMPLETED', 1, '2026-09-02 20:52:52.453491', 1, '2026-09-02 20:52:52.453491', false);
INSERT INTO public.sale VALUES (67, 'INV-1788382372540', 1, NULL, '2026-09-03 00:00:00', 12000.00, 2160.00, 0.00, 14160.00, 'COMPLETED', 1, '2026-09-02 20:53:32.041049', 1, '2026-09-02 20:53:32.041049', false);
INSERT INTO public.sale VALUES (68, 'INV-1788460404287', 1, NULL, '2026-09-04 00:00:00', 125.00, 22.50, 0.00, 147.50, 'COMPLETED', 1, '2026-09-03 18:33:47.703257', 1, '2026-09-03 18:33:47.703257', false);
INSERT INTO public.sale VALUES (69, 'INV-1788518044256', 1, NULL, '2026-09-04 00:00:00', 449.00, 80.82, 0.00, 529.82, 'COMPLETED', 1, '2026-09-04 10:35:25.750919', 1, '2026-09-04 10:35:25.750919', false);
INSERT INTO public.sale VALUES (70, 'INV-1788520569536', 1, NULL, '2026-09-04 00:00:00', 30.00, 3.00, 0.00, 33.00, 'COMPLETED', 1, '2026-09-04 11:17:41.537012', 1, '2026-09-04 11:17:41.537012', false);


--
-- TOC entry 5141 (class 0 OID 21034)
-- Dependencies: 244
-- Data for Name: sale_item; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.sale_item VALUES (2, 15, 2, 1.000, 200.00, 12.00, 224.00);
INSERT INTO public.sale_item VALUES (3, 15, 3, 2.000, 200.00, 10.00, 440.00);
INSERT INTO public.sale_item VALUES (4, 16, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (6, 17, 2, 2.000, 200.00, 12.00, 448.00);
INSERT INTO public.sale_item VALUES (7, 17, 3, 1.000, 200.00, 10.00, 220.00);
INSERT INTO public.sale_item VALUES (8, 18, 2, 2.000, 200.00, 12.00, 448.00);
INSERT INTO public.sale_item VALUES (10, 19, 3, 2.000, 200.00, 10.00, 440.00);
INSERT INTO public.sale_item VALUES (12, 20, 1, 2.000, 125.00, 18.00, 295.00);
INSERT INTO public.sale_item VALUES (80, 33, 3, 7.000, 200.00, 10.00, 1540.00);
INSERT INTO public.sale_item VALUES (18, 23, 2, 2.000, 200.00, 12.00, 448.00);
INSERT INTO public.sale_item VALUES (83, 36, 3, 3.000, 200.00, 10.00, 660.00);
INSERT INTO public.sale_item VALUES (89, 37, 4, 2.000, 12000.00, 10.00, 26400.00);
INSERT INTO public.sale_item VALUES (92, 39, 2, 2.000, 200.00, 12.00, 448.00);
INSERT INTO public.sale_item VALUES (93, 40, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (94, 41, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (95, 38, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (98, 44, 1, 2.000, 125.00, 18.00, 295.00);
INSERT INTO public.sale_item VALUES (101, 45, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (102, 46, 1, 2.000, 125.00, 18.00, 295.00);
INSERT INTO public.sale_item VALUES (103, 46, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (104, 47, 1, 2.000, 125.00, 18.00, 295.00);
INSERT INTO public.sale_item VALUES (42, 26, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (105, 47, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (106, 42, 1, 2.000, 125.00, 18.00, 295.00);
INSERT INTO public.sale_item VALUES (110, 48, 7, 1.000, 12000.00, 18.00, 14160.00);
INSERT INTO public.sale_item VALUES (111, 49, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (112, 50, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (113, 51, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (52, 25, 3, 3.000, 200.00, 10.00, 660.00);
INSERT INTO public.sale_item VALUES (53, 24, 2, 9.000, 200.00, 12.00, 2016.00);
INSERT INTO public.sale_item VALUES (114, 52, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (56, 21, 2, 3.000, 200.00, 12.00, 672.00);
INSERT INTO public.sale_item VALUES (57, 21, 3, 1.000, 200.00, 10.00, 220.00);
INSERT INTO public.sale_item VALUES (58, 27, 2, 1.000, 200.00, 12.00, 224.00);
INSERT INTO public.sale_item VALUES (59, 28, 2, 1.000, 200.00, 12.00, 224.00);
INSERT INTO public.sale_item VALUES (60, 29, 2, 1.000, 200.00, 12.00, 224.00);
INSERT INTO public.sale_item VALUES (115, 53, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (117, 55, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (120, 57, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (121, 57, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (122, 54, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (123, 43, 1, 2.000, 125.00, 18.00, 295.00);
INSERT INTO public.sale_item VALUES (125, 56, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (126, 58, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (133, 62, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (134, 63, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (135, 64, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (136, 65, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (137, 66, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (138, 67, 7, 1.000, 12000.00, 18.00, 14160.00);
INSERT INTO public.sale_item VALUES (139, 68, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (140, 60, 1, 1.000, 125.00, 18.00, 147.50);
INSERT INTO public.sale_item VALUES (141, 60, 4, 1.000, 12000.00, 10.00, 13200.00);
INSERT INTO public.sale_item VALUES (142, 69, 8, 1.000, 449.00, 18.00, 529.82);
INSERT INTO public.sale_item VALUES (143, 70, 10, 2.000, 10.00, 10.00, 22.00);
INSERT INTO public.sale_item VALUES (144, 70, 11, 1.000, 10.00, 10.00, 11.00);


--
-- TOC entry 5143 (class 0 OID 21058)
-- Dependencies: 246
-- Data for Name: payment; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.payment VALUES (3, 21, 'CUSTOMER', NULL, NULL, 882.00, 'CASH', '2026-08-26 20:58:04.205398', NULL, 1, '2026-08-26 15:28:04.207415', 1, '2026-08-26 15:28:04.207415', false);
INSERT INTO public.payment VALUES (4, 27, 'CUSTOMER', NULL, NULL, 224.00, 'CASH', '2026-08-26 21:57:24.832244', NULL, 1, '2026-08-26 16:27:24.848486', 1, '2026-08-26 16:27:24.848486', false);
INSERT INTO public.payment VALUES (5, 28, 'CUSTOMER', NULL, NULL, 224.00, 'CASH', '2026-08-26 22:09:24.290492', NULL, 1, '2026-08-26 16:39:24.290952', 1, '2026-08-26 16:39:24.290952', false);
INSERT INTO public.payment VALUES (6, 29, 'CUSTOMER', NULL, NULL, 224.00, 'CASH', '2026-08-26 22:09:40.791656', NULL, 1, '2026-08-26 16:39:40.791656', 1, '2026-08-26 16:39:40.791656', false);
INSERT INTO public.payment VALUES (7, 33, 'CUSTOMER', NULL, NULL, 1540.00, 'CASH', '2026-08-26 23:08:56.075211', NULL, 1, '2026-08-26 17:38:56.078701', 1, '2026-08-26 17:38:56.078701', false);
INSERT INTO public.payment VALUES (8, 36, 'CUSTOMER', NULL, NULL, 660.00, 'CASH', '2026-08-26 23:10:06.398188', NULL, 1, '2026-08-26 17:40:06.398188', 1, '2026-08-26 17:40:06.398188', false);
INSERT INTO public.payment VALUES (9, 37, 'CUSTOMER', NULL, NULL, 200.00, 'CASH', '2026-08-27 00:21:08.248677', NULL, 1, '2026-08-26 18:51:08.255959', 1, '2026-08-26 18:51:08.255959', false);
INSERT INTO public.payment VALUES (10, 39, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-27 01:42:48.026672', NULL, 1, '2026-08-26 20:12:48.033949', 1, '2026-08-26 20:12:48.033949', false);
INSERT INTO public.payment VALUES (11, 39, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-27 10:13:39.112914', NULL, 1, '2026-08-27 04:43:39.119262', 1, '2026-08-27 04:43:39.119262', false);
INSERT INTO public.payment VALUES (12, 40, 'CUSTOMER', NULL, NULL, 200.00, 'CASH', '2026-08-27 11:22:19.102862', NULL, 1, '2026-08-27 05:52:19.109783', 1, '2026-08-27 05:52:19.109783', false);
INSERT INTO public.payment VALUES (13, 40, 'CUSTOMER', NULL, NULL, 200.00, 'CASH', '2026-08-27 11:37:58.438208', NULL, 1, '2026-08-27 06:07:58.826101', 1, '2026-08-27 06:07:58.826101', false);
INSERT INTO public.payment VALUES (14, 40, 'CUSTOMER', NULL, NULL, 200.00, 'CASH', '2026-08-27 11:38:25.839953', NULL, 1, '2026-08-27 06:08:25.839953', 1, '2026-08-27 06:08:25.839953', false);
INSERT INTO public.payment VALUES (15, 39, 'CUSTOMER', NULL, NULL, 200.00, 'CASH', '2026-08-27 11:38:45.060884', NULL, 1, '2026-08-27 06:08:45.061881', 1, '2026-08-27 06:08:45.061881', false);
INSERT INTO public.payment VALUES (16, 41, 'CUSTOMER', NULL, NULL, 12.00, 'CASH', '2026-08-27 11:39:09.432401', NULL, 1, '2026-08-27 06:09:09.432401', 1, '2026-08-27 06:09:09.432401', false);
INSERT INTO public.payment VALUES (17, 44, 'CUSTOMER', NULL, NULL, 100.00, 'CASH', '2026-08-28 19:32:20.791899', NULL, 1, '2026-08-28 14:02:20.797377', 1, '2026-08-28 14:02:20.797377', false);
INSERT INTO public.payment VALUES (18, 44, 'CUSTOMER', NULL, NULL, 100.00, 'CASH', '2026-08-28 19:45:30.430249', NULL, 1, '2026-08-28 14:15:30.461233', 1, '2026-08-28 14:15:30.461233', false);
INSERT INTO public.payment VALUES (19, 44, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-28 20:55:46.007371', NULL, 2, '2026-08-28 15:25:46.048693', 2, '2026-08-28 15:25:46.048693', false);
INSERT INTO public.payment VALUES (20, 45, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-28 21:04:01.759981', NULL, 1, '2026-08-28 15:34:01.775422', 1, '2026-08-28 15:34:01.775422', false);
INSERT INTO public.payment VALUES (21, 45, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-28 21:05:32.260275', NULL, 1, '2026-08-28 15:35:32.277805', 1, '2026-08-28 15:35:32.277805', false);
INSERT INTO public.payment VALUES (22, 46, 'CUSTOMER', NULL, NULL, 1000.00, 'CASH', '2026-08-28 23:16:50.559606', NULL, 1, '2026-08-28 17:46:50.566628', 1, '2026-08-28 17:46:50.566628', false);
INSERT INTO public.payment VALUES (23, 47, 'CUSTOMER', NULL, NULL, 13495.00, 'CASH', '2026-08-28 23:18:03.578311', NULL, 1, '2026-08-28 17:48:03.579313', 1, '2026-08-28 17:48:03.579313', false);
INSERT INTO public.payment VALUES (24, 45, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-29 15:49:44.689396', NULL, 1, '2026-08-29 10:19:44.802765', 1, '2026-08-29 10:19:44.802765', false);
INSERT INTO public.payment VALUES (25, 46, 'CUSTOMER', NULL, NULL, 100.00, 'CASH', '2026-08-29 16:49:42.610444', NULL, 1, '2026-08-29 11:19:42.678958', 1, '2026-08-29 11:19:42.678958', false);
INSERT INTO public.payment VALUES (26, 44, 'CUSTOMER', NULL, NULL, 20.00, 'CASH', '2026-08-29 16:49:50.61256', NULL, 1, '2026-08-29 11:19:50.61256', 1, '2026-08-29 11:19:50.61256', false);
INSERT INTO public.payment VALUES (27, 46, 'CUSTOMER', NULL, NULL, 1000.00, 'CASH', '2026-08-29 20:58:57.165677', NULL, 1, '2026-08-29 15:28:57.516448', 1, '2026-08-29 15:28:57.516448', false);
INSERT INTO public.payment VALUES (28, 46, 'CUSTOMER', NULL, NULL, 5000.00, 'CASH', '2026-08-29 20:59:32.292755', NULL, 1, '2026-08-29 15:29:32.29348', 1, '2026-08-29 15:29:32.29348', false);
INSERT INTO public.payment VALUES (29, 45, 'CUSTOMER', NULL, NULL, 117.50, 'CASH', '2026-08-29 21:04:23.02197', NULL, 1, '2026-08-29 15:34:23.024759', 1, '2026-08-29 15:34:23.024759', false);
INSERT INTO public.payment VALUES (30, 46, 'CUSTOMER', NULL, NULL, 100.00, 'CASH', '2026-08-29 21:40:11.821992', NULL, 1, '2026-08-29 16:10:11.864383', 1, '2026-08-29 16:10:11.864383', false);
INSERT INTO public.payment VALUES (31, 46, 'CUSTOMER', NULL, NULL, 100.00, 'CASH', '2026-08-29 22:02:56.646871', NULL, 1, '2026-08-29 16:32:56.646871', 1, '2026-08-29 16:32:56.646871', false);
INSERT INTO public.payment VALUES (32, 55, 'CUSTOMER', NULL, NULL, 10.00, 'CASH', '2026-08-29 22:31:30.956753', NULL, 1, '2026-08-29 17:01:30.957899', 1, '2026-08-29 17:01:30.957899', false);
INSERT INTO public.payment VALUES (33, 56, 'CUSTOMER', NULL, NULL, 40.00, 'CASH', '2026-08-29 22:35:24.237926', NULL, 1, '2026-08-29 17:05:24.237926', 1, '2026-08-29 17:05:24.237926', false);
INSERT INTO public.payment VALUES (34, 56, 'CUSTOMER', NULL, NULL, 107.50, 'CASH', '2026-08-29 22:42:15.24192', NULL, 1, '2026-08-29 17:12:15.24192', 1, '2026-08-29 17:12:15.24192', false);
INSERT INTO public.payment VALUES (35, 57, 'CUSTOMER', NULL, NULL, 10000.00, 'CASH', '2026-08-29 22:48:22.731183', NULL, 1, '2026-08-29 17:18:22.738048', 1, '2026-08-29 17:18:22.738048', false);
INSERT INTO public.payment VALUES (36, 46, 'CUSTOMER', NULL, NULL, 100.00, 'CASH', '2026-09-01 19:30:41.714016', NULL, 1, '2026-09-01 14:00:41.996473', 1, '2026-09-01 14:00:41.996473', false);
INSERT INTO public.payment VALUES (37, 58, 'CUSTOMER', NULL, NULL, 12.00, 'CASH', '2026-09-02 20:45:16.701051', NULL, 1, '2026-09-02 15:15:16.705586', 1, '2026-09-02 15:15:16.705586', false);
INSERT INTO public.payment VALUES (38, 62, 'CUSTOMER', NULL, NULL, 147.50, 'CASH', '2026-09-03 01:51:06.287534', NULL, 1, '2026-09-02 20:21:06.289751', 1, '2026-09-02 20:21:06.289751', false);
INSERT INTO public.payment VALUES (39, 63, 'CUSTOMER', NULL, NULL, 147.50, 'CASH', '2026-09-03 01:58:28.667338', NULL, 1, '2026-09-02 20:28:28.667338', 1, '2026-09-02 20:28:28.667338', false);
INSERT INTO public.payment VALUES (40, 64, 'CUSTOMER', NULL, NULL, 147.50, 'CASH', '2026-09-03 02:21:34.167291', NULL, 1, '2026-09-02 20:51:34.168419', 1, '2026-09-02 20:51:34.168419', false);
INSERT INTO public.payment VALUES (41, 65, 'CUSTOMER', NULL, NULL, 13200.00, 'CASH', '2026-09-03 02:22:19.665239', NULL, 1, '2026-09-02 20:52:19.665239', 1, '2026-09-02 20:52:19.665239', false);
INSERT INTO public.payment VALUES (42, 66, 'CUSTOMER', NULL, NULL, 13200.00, 'CASH', '2026-09-03 02:22:52.46812', NULL, 1, '2026-09-02 20:52:52.46812', 1, '2026-09-02 20:52:52.46812', false);
INSERT INTO public.payment VALUES (43, 67, 'CUSTOMER', NULL, NULL, 14160.00, 'CASH', '2026-09-03 02:23:32.047621', NULL, 1, '2026-09-02 20:53:32.048779', 1, '2026-09-02 20:53:32.048779', false);
INSERT INTO public.payment VALUES (44, 68, 'CUSTOMER', NULL, NULL, 147.50, 'CASH', '2026-09-04 00:03:48.389013', NULL, 1, '2026-09-03 18:33:48.408991', 1, '2026-09-03 18:33:48.408991', false);
INSERT INTO public.payment VALUES (45, 69, 'CUSTOMER', NULL, NULL, 529.82, 'CASH', '2026-09-04 16:05:26.067861', NULL, 1, '2026-09-04 10:35:26.073327', 1, '2026-09-04 10:35:26.073327', false);
INSERT INTO public.payment VALUES (46, 70, 'CUSTOMER', NULL, NULL, 33.00, 'CASH', '2026-09-04 16:47:41.609126', NULL, 1, '2026-09-04 11:17:41.612811', 1, '2026-09-04 11:17:41.612811', false);


