-- Give each generated demo product its own image for its specific product type.
UPDATE product_images pi
SET image_url = (
    SELECT image_map.image_url
    FROM products p
    JOIN (
SELECT 1 AS sequence_number, 'https://images.squarespace-cdn.com/content/v1/5d11fd45b9e7020001a6a368/bcd00582-068c-4bdd-bea8-f3ffeeb1b796/SH-66_2.png' AS image_url
UNION ALL SELECT 2 AS sequence_number, 'https://jpidisplay.com/cdn/shop/files/12-x-8-bam-boo-natural-bamboo-modern-serving-tray-with-handles-main_394x.png?v=1683843236' AS image_url
UNION ALL SELECT 3 AS sequence_number, 'https://barkhabeauty.com/cdn/shop/files/pinkiepie_878x_crop_center.jpg?v=1760428768' AS image_url
UNION ALL SELECT 4 AS sequence_number, 'https://www.pngitem.com/pimgs/m/558-5581363_denim-jacket-men-png-transparent-png.png' AS image_url
UNION ALL SELECT 5 AS sequence_number, 'https://assets.pbimgs.com/pbimgs/rk/images/dp/wcm/202234/0513/stowe-modern-metal-frames-13-z.jpg' AS image_url
UNION ALL SELECT 6 AS sequence_number, 'https://ship.erewhon.com/cdn/shop/files/HYDRATINGSHEETMASK_PERSONALCARE_BEAUTY_SHOPIFY_1.png?v=1769795076' AS image_url
UNION ALL SELECT 7 AS sequence_number, 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/1.webp' AS image_url
UNION ALL SELECT 8 AS sequence_number, 'https://media.homeboxstores.com/i/homebox/168127741-168127741-HMBX21082025N_05-2100.jpg' AS image_url
UNION ALL SELECT 9 AS sequence_number, 'https://www.organiercare.com/cdn/shop/files/sun-screen.webp?v=1766762081&width=1920' AS image_url
UNION ALL SELECT 10 AS sequence_number, 'https://www.fotoboxpro.com/wp-content/uploads/kot-pantolon-dekupe-fotograf-cekimi-zara-tarzi-1.jpg' AS image_url
UNION ALL SELECT 11 AS sequence_number, 'https://www.petite-plume.com/cdn/shop/files/HETHRBEI_Beige_1_b5049474-4b00-442f-a58e-d8048c1928de.jpg?v=1727195075&width=2400' AS image_url
UNION ALL SELECT 12 AS sequence_number, 'https://cdn.tirabeauty.com/v2/billowing-snowflake-434234/tira-p/wrkr/products/pictures/item/free/original/YyIgl4MgtQ-1072684_1.jpg' AS image_url
UNION ALL SELECT 13 AS sequence_number, 'https://cdn11.bigcommerce.com/s-p6270ymd/images/stencil/1280x1280/products/12766/126944/2__09146.1679332779.jpg?c=2%3Fimbypass%3Don' AS image_url
UNION ALL SELECT 14 AS sequence_number, 'https://stofferhome.com/cdn/shop/products/ScreenShot2022-04-19at11.59.20AM_1800x1800.png?v=1710437410' AS image_url
UNION ALL SELECT 15 AS sequence_number, 'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/1.webp' AS image_url
UNION ALL SELECT 16 AS sequence_number, 'https://resource.tmix.jp/product_specifications/image_6b178b616bb3c353cd39998edb2c6cc8a9daea1afa5338cc2df4740c71cdc24e.jpg' AS image_url
UNION ALL SELECT 17 AS sequence_number, 'https://bouquetier.fr/cdn/shop/files/DEQ21WES16712-176543_d24ec9e8db3ec1d413da03b7a3a5f518_dtl_1_1.png?v=1757873838&width=2400' AS image_url
UNION ALL SELECT 18 AS sequence_number, 'https://dermasation.com/cdn/shop/files/URS04233.jpg?v=1758377170&width=3840' AS image_url
UNION ALL SELECT 19 AS sequence_number, 'https://cdn.dummyjson.com/product-images/tops/blue-frock/1.webp' AS image_url
UNION ALL SELECT 20 AS sequence_number, 'https://img.pikbest.com/png-images/20241219/a-simple-white-ceramic-mug-with-a-smooth-finish-displayed-on-clean-background-_11273816.png%21sw800' AS image_url
UNION ALL SELECT 21 AS sequence_number, 'https://www.shophalaal.com/cdn/shop/files/Untitled-1_2_1200x1200.jpg?v=1737283450' AS image_url
UNION ALL SELECT 22 AS sequence_number, 'https://cdn.convertri.com/d72e79cc-9341-11ea-abef-0697e5ca793e/aadfdd482725ce16c237cc8cf6f9a6a9759d6e14/flat-lay-sweater-front-view_4.jpg' AS image_url
UNION ALL SELECT 23 AS sequence_number, 'https://media.homecentre.com/i/homecentre/167226630-167226630-HC11122024_01-2100.jpg' AS image_url
UNION ALL SELECT 24 AS sequence_number, 'https://www.mera-wellness.com/cdn/shop/files/MeraHydratingFaceSerum_1024x.jpg?v=1696997346' AS image_url
UNION ALL SELECT 25 AS sequence_number, 'https://www.rickracktextiles.com/cdn/shop/files/SandersButtonUpFlatlayShortSleeve_1100x.jpg?v=1771964794' AS image_url
UNION ALL SELECT 26 AS sequence_number, 'https://jpidisplay.com/cdn/shop/files/17-75-x-13-bam-boo-natural-bamboo-large-serving-tray-with-handles.png?v=1741649554' AS image_url
UNION ALL SELECT 27 AS sequence_number, 'https://barkhabeauty.com/cdn/shop/files/Apple-Bloom_Open_Lip-Tint.webp?v=1758189514&width=3840' AS image_url
UNION ALL SELECT 28 AS sequence_number, 'https://www.thebudgetbabe.com/uploads/2018/201801/universalthreadstargetlookbook_-_14.jpg' AS image_url
UNION ALL SELECT 29 AS sequence_number, 'https://assets.pbimgs.com/pbimgs/rk/images/dp/wcm/202610/0536/reese-frames-12-o.jpg' AS image_url
UNION ALL SELECT 30 AS sequence_number, 'https://media.gazelook.id/product_image/product_imageDPz3.png' AS image_url
UNION ALL SELECT 31 AS sequence_number, 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/2.webp' AS image_url
UNION ALL SELECT 32 AS sequence_number, 'https://cb.scene7.com/is/image/Crate/CorfuWhtTblLmpDrumShdROS22/%24web_plp_card_hires%24/240201121418/corfu-cream-table-lamp-with-linen-drum-shade.jpg' AS image_url
UNION ALL SELECT 33 AS sequence_number, 'https://purelogica.in/cdn/shop/files/T3_care_post_76.png?v=1775638112' AS image_url
UNION ALL SELECT 34 AS sequence_number, 'https://orbitvu.fr/wp-content/uploads/2022/02/typical-flat-lay-arrangement-produt-photography.jpg' AS image_url
UNION ALL SELECT 35 AS sequence_number, 'https://i.ebayimg.com/images/g/Rw4AAOSwkw9jGdHS/s-l1600.jpg' AS image_url
UNION ALL SELECT 36 AS sequence_number, 'https://assets.woolworthsstatic.co.za/Face-Toner-for-Normal-and-Combination-Skin-250-ml-6009217061358.jpg?V=%244Kz&o=eyJidWNrZXQiOiJ3dy1vbmxpbmUtaW1hZ2UtcmVzaXplIiwia2V5IjoiaW1hZ2VzL2VsYXN0aWNlcmEvcHJvZHVjdHMvaGVyby8yMDIwLTA3LTEwLzYwMDkyMTcwNjEzNThfaGVyby5qcGcifQ' AS image_url
UNION ALL SELECT 37 AS sequence_number, 'https://cdn11.bigcommerce.com/s-p6270ymd/images/stencil/1280x1280/products/12766/100333/DSC_0769__39162.1582041456.jpg?c=2%3Fimbypass%3Don' AS image_url
UNION ALL SELECT 38 AS sequence_number, 'https://earlysettler.com.au/cdn/shop/files/14e9fccd3e93ccd8ffd52ee956e675e8419d1c1b_90024906_01_web_2048x.jpg?v=1771804375' AS image_url
UNION ALL SELECT 39 AS sequence_number, 'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/2.webp' AS image_url
UNION ALL SELECT 40 AS sequence_number, 'https://www.blanks4brands.com/cdn/shop/files/2_a59fb02d-0558-4ac6-9eb5-604b76bc7bec.png?v=1779749245' AS image_url
UNION ALL SELECT 41 AS sequence_number, 'https://images.tcdn.com.br/img/img_prod/1464992/vaso_gota_londres_branco_1023_1_05d1d8a5d088f3b27c8078808f98b7d9.jpg' AS image_url
UNION ALL SELECT 42 AS sequence_number, 'https://amboskincare.com/cdn/shop/files/new_900x900.webp?v=1695809381' AS image_url
UNION ALL SELECT 43 AS sequence_number, 'https://cdn.dummyjson.com/product-images/tops/blue-frock/2.webp' AS image_url
UNION ALL SELECT 44 AS sequence_number, 'https://original-naire.com/web-box/upload/item/191141.jpg' AS image_url
UNION ALL SELECT 45 AS sequence_number, 'https://grancestore.com/cdn/shop/files/WhatsAppImage2026-07-07at3.46.06AM_2.webp?v=1783421348&width=1445' AS image_url
UNION ALL SELECT 46 AS sequence_number, 'https://product.hstatic.net/200000201725/product/z4979288927353_deb9c068095f2116fe7089e415a7ffde_99ff75a6dc764ecb91d3aa9f6cb90d1a_master.jpg' AS image_url
UNION ALL SELECT 47 AS sequence_number, 'https://bamboofromvietnam.com/upload/product/a01-storage/a01-basket/b02/39-1.jpg?v=1.0.0' AS image_url
UNION ALL SELECT 48 AS sequence_number, 'https://mymeant.com/cdn/shop/files/MEANT_Hydrating_Face_Serum_with_Advanced_Hyaluronic_Acid_for_Intense_Hydration_Bottle.png?v=1749749848&width=1445' AS image_url
UNION ALL SELECT 49 AS sequence_number, 'https://cdn.shopify.com/s/files/1/0207/9406/files/01_Linen_Shirt_Oatmeal_Flat.jpg?v=1745325701&width=600' AS image_url
UNION ALL SELECT 50 AS sequence_number, 'https://jpidisplay.com/cdn/shop/files/20-x-13-bam-boo-natural-bamboo-large-serving-tray-with-handles.png?v=1762528415' AS image_url
UNION ALL SELECT 51 AS sequence_number, 'https://cdn2.blanxer.com/uploads/654dc303c8ce9e2df20d61fe/product_image-tints-%281%29-6299.webp' AS image_url
UNION ALL SELECT 52 AS sequence_number, 'https://www.vestiai.com.br/landing/before-after/antes-jaqueta.webp' AS image_url
UNION ALL SELECT 53 AS sequence_number, 'https://www.potterybarn.co.uk/site/PB/Product%20Images/stowe-modern-metal-frames-11-x-13-202520-0005-stowe-modern-metal-frames-11x13-3-z.jpg?resizeh=960&resizeid=70&resizew=960' AS image_url
UNION ALL SELECT 54 AS sequence_number, 'https://dtgxwmigmg3gc.cloudfront.net/imagery/assets/derivations/icon/512/512/true/eyJpZCI6ImM5MzQ4YzJiNTI1MTllMGZhZTk2NzZhMmFmNGE2ZWQ3Iiwic3RvcmFnZSI6InB1YmxpY19zdG9yZSJ9?signature=6b1f1d8ffe830909513fe10220f0a6c124e533934c34eb60ca0de30e693e3547' AS image_url
UNION ALL SELECT 55 AS sequence_number, 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/3.webp' AS image_url
UNION ALL SELECT 56 AS sequence_number, 'https://cdn.sklum.com/es/wk/4551780/lampara-de-mesa-en-ceramica-y-tela-kenaris.jpg?cf-resize=gallery' AS image_url
UNION ALL SELECT 57 AS sequence_number, 'https://dermalondon.com.pk/cdn/shop/files/sunscreen-Camera.jpg?v=1772089414' AS image_url
UNION ALL SELECT 58 AS sequence_number, 'https://images.squarespace-cdn.com/content/v1/6436e68b3e41f224b8b0a966/136ba57e-557a-47e3-9c2c-70aae4b73605/trouser-for-flat-lay-photography.jpg?format=500w' AS image_url
UNION ALL SELECT 59 AS sequence_number, 'https://posudacenter.ru/upload/iblock/4f4/4f4de9911435f2f9b0a35bd6d7b215df.jpg' AS image_url
UNION ALL SELECT 60 AS sequence_number, 'https://www.mbodhii.com/cdn/shop/files/mbodhii-Hydrating-Facial-Toner-bottle.jpg?v=1765872980&width=1445' AS image_url
UNION ALL SELECT 61 AS sequence_number, 'https://www.fgear.in/cdn/shop/files/2_17a06ad2-aea9-481d-983c-760a86d04cf3.png?v=1772867154&width=1066' AS image_url
UNION ALL SELECT 62 AS sequence_number, 'https://www.adairs.com.au/globalassets/catalogs/homewares/cushions/homerepublic_6/41634_natural_zoom_01.jpg' AS image_url
UNION ALL SELECT 63 AS sequence_number, 'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/3.webp' AS image_url
UNION ALL SELECT 64 AS sequence_number, 'https://sitio2021.matizsae.com.py/1397-large_default/remeras-de-algodon-endure.jpg' AS image_url
UNION ALL SELECT 65 AS sequence_number, 'https://shop.nf.net.au/Products/CNT011.jpg' AS image_url
UNION ALL SELECT 66 AS sequence_number, 'https://crunchi.com/cdn/shop/files/Gentle-Facial-Cleanser-Cart-Images-01.jpg?crop=center&height=1200&v=1775496405&width=1200' AS image_url
UNION ALL SELECT 67 AS sequence_number, 'https://cdn.dummyjson.com/product-images/tops/blue-frock/3.webp' AS image_url
UNION ALL SELECT 68 AS sequence_number, 'https://www.sitografika.com.mk/Content/dataImages/galleries/big/subl_bela_blanko_1.png' AS image_url
UNION ALL SELECT 69 AS sequence_number, 'https://www.miadelmar.com/cdn/shop/products/Mia-del-Mar-Night_Cream_M2_1080x.png?v=1562008747' AS image_url
UNION ALL SELECT 70 AS sequence_number, 'https://product.hstatic.net/200000201725/product/z4979288925095_941eba70efa040fcf383528c1310e36f_70000233c58744aa9dabd55ef5d6f6f8_master.jpg' AS image_url
UNION ALL SELECT 71 AS sequence_number, 'https://down-br.img.susercontent.com/file/sg-11134201-7rdvu-m1enpqv7xj4o67' AS image_url
UNION ALL SELECT 72 AS sequence_number, 'https://mymeant.com/cdn/shop/files/MEANT_Hydrating_Face_Serum_with_Advanced_Hyaluronic_Acid_for_Intense_Hydration_bottle_in_air.png?v=1772726222&width=1024' AS image_url
UNION ALL SELECT 73 AS sequence_number, 'https://permanent.mx/cdn/shop/files/44_c5adbcca-e29f-4a47-92a0-1a488ebf4e16.png?v=1718657005&width=533' AS image_url
UNION ALL SELECT 74 AS sequence_number, 'https://bamboomn.com/cdn/shop/products/bamboo-serving-tray-with-handles-bdst-001-01a.jpg?v=1628876087' AS image_url
UNION ALL SELECT 75 AS sequence_number, 'https://down-br.img.susercontent.com/file/br-11134207-81z1k-mg6yuwvqiosncd' AS image_url
UNION ALL SELECT 76 AS sequence_number, 'https://img77.uenicdn.com/image/upload/v1531933073/service_images/shutterstock_647008975.jpg' AS image_url
UNION ALL SELECT 77 AS sequence_number, 'https://assets.pbimgs.com/pbimgs/ab/images/dp/wcm/202612/0849/stowe-modern-metal-frames-14x17-2-o.jpg' AS image_url
UNION ALL SELECT 78 AS sequence_number, 'https://ship.erewhon.com/cdn/shop/files/HYDRATING_SHEET_MASK_PERSONAL_CARE_BEAUTY_SHOPIFY_1_1.png?crop=center&height=1080&v=1776467536&width=1080' AS image_url
UNION ALL SELECT 79 AS sequence_number, 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/4.webp' AS image_url
UNION ALL SELECT 80 AS sequence_number, 'https://www.thebaliconcept.co.uk/cdn/shop/files/SenjaLamp1.png?v=1744023389&width=1024' AS image_url
UNION ALL SELECT 81 AS sequence_number, 'https://riveladermascience.com/cdn/shop/files/Rivela-Sunscreen-Lotion-SPF-50-Rivela-Dermascience-29464732106958_1024x1024.jpg?v=1755863245' AS image_url
UNION ALL SELECT 82 AS sequence_number, 'https://img.magnific.com/free-photo/jeans_1203-8093.jpg?q=80&semt=ais_hybrid&w=740' AS image_url
UNION ALL SELECT 83 AS sequence_number, 'https://www.sashaaworld.com/cdn/shop/files/3_12a7bb0b-29d8-427d-a736-87d0fb02c8a9.png?v=1747205583&width=1200' AS image_url
UNION ALL SELECT 84 AS sequence_number, 'https://veehabeauty.com/cdn/shop/files/41XHEKOMW1L._SL1200.jpg?v=1774358626' AS image_url
UNION ALL SELECT 85 AS sequence_number, 'https://www.xbzbrindes.com.br/img/produtos/3/Bolsa-de-Ombro-Nylon-17721-1702554987.jpg' AS image_url
UNION ALL SELECT 86 AS sequence_number, 'https://bymolle.com/cdn/shop/products/by-molle-linnen-kussen-flax.jpg?v=1599637069' AS image_url
UNION ALL SELECT 87 AS sequence_number, 'https://cdn.dummyjson.com/product-images/fragrances/chanel-coco-noir-eau-de/1.webp' AS image_url
UNION ALL SELECT 88 AS sequence_number, 'https://s.alicdn.com/%40sc04/kf/S3a9ec76553cc46618ea8984789034b44B/140g-Wholesale-Good-Price-Less-Than-1.00-Custom-logo-Printing-Blank-White-Cotton-Fabric-Round-Neck-Economy-Quality-Man-t-Shirt.jpg' AS image_url
UNION ALL SELECT 89 AS sequence_number, 'https://www.afkaryexpo.com/web/image/product.template/13190/image_512' AS image_url
UNION ALL SELECT 90 AS sequence_number, 'https://glownglee.com/cdn/shop/files/IMG_8930.jpg?v=1716918037' AS image_url
UNION ALL SELECT 91 AS sequence_number, 'https://cdn.dummyjson.com/product-images/tops/blue-frock/4.webp' AS image_url
UNION ALL SELECT 92 AS sequence_number, 'https://i5.walmartimages.ca/images/Enlarge/604/555/6000207604555.jpg?odnBg=FFFFFF&odnHeight=612&odnWidth=612' AS image_url
UNION ALL SELECT 93 AS sequence_number, 'https://miera.in/cdn/shop/files/MIERANIGHTCREAM.jpg?v=1743431264&width=1346' AS image_url
UNION ALL SELECT 94 AS sequence_number, 'https://images.rawpixel.com/image_social_square/cHJpdmF0ZS9sci9pbWFnZXMvd2Vic2l0ZS8yMDI0LTA2L3Jhd3BpeGVsX29mZmljZV8zNV9waG90b19vZl9hX3Bhc3RlbF9icm93bl9jYXJkaWdhbl9odW5nX29uX2Ffd18zOWUzOGZlNC1mZmRhLTRkZmQtYjAwOS1jM2U3ZmM2OTM5NDJfMS5qcGc.jpg' AS image_url
UNION ALL SELECT 95 AS sequence_number, 'https://d2lnr5mha7bycj.cloudfront.net/product-image/file/large_fd7abd69-8396-4e1c-8953-25c45aa4d592.jpg' AS image_url
UNION ALL SELECT 96 AS sequence_number, 'https://bbabysuleman.com/cdn/shop/products/serum_6f50b912-a6a3-44d2-ac05-c681497dc8da.jpg?v=1712182549&width=1066' AS image_url
UNION ALL SELECT 97 AS sequence_number, 'https://baseec-img-mng.akamaized.net/images/item/origin/7902e7a1e88511860e67cb5bb49476dd.jpg?im=Resize%2Cwidth%3D1280%2Ctype%3Dnormal&imformat=generic&q=90' AS image_url
UNION ALL SELECT 98 AS sequence_number, 'https://ferreteriavidri.com/images/items/large/464319.jpg' AS image_url
UNION ALL SELECT 99 AS sequence_number, 'https://www.mocoallure.com.mx/cdn/shop/files/4_13.png?v=1782938234&width=767' AS image_url
UNION ALL SELECT 100 AS sequence_number, 'https://images.squarespace-cdn.com/content/v1/63cfc207f74f3448fae9f922/401d46fe-edcb-4d60-b831-88b2b24f5014/WORK_ECOMM_153%2Bcopy%2B2.jpg' AS image_url
    ) image_map
      ON p.name LIKE 'Senvia Demo ' || CAST(image_map.sequence_number AS VARCHAR) || ' -%'
    WHERE p.id = pi.product_id
)
WHERE pi.public_id LIKE 'external-demo:%'
  AND EXISTS (
      SELECT 1 FROM products p
      WHERE p.id = pi.product_id AND p.name LIKE 'Senvia Demo %'
  );

-- Match the original demo catalog products to their named product photos.
UPDATE product_images pi
SET image_url = (
    SELECT CASE p.name
        WHEN 'Canvas Tote Bag' THEN 'https://butterflai.pro/_next/image?dpl=dpl_DrmBT4dyokLAyzPXqPEjuSDXkbBB&q=75&url=https%3A%2F%2Fgjuegaszjygmxzuscvhs.supabase.co%2Fstorage%2Fv1%2Fobject%2Fgallery_multimedia%2FBag%2Fcanvas_tote_bag_studio.webp&w=3840'
        WHEN 'Linen Shirt' THEN 'https://cdn.pixlio.net/assets/image/remove-wrinkles-from-photo/wrinkle-before-clothing.webp'
        WHEN 'Brass Table Lamp' THEN 'https://cb.scene7.com/is/image/Crate/CorfuWhtTblLmpLnTprShdROS22/%24web_plp_card_hires%24/240201121418/corfu-cream-table-lamp-with-linen-taper-shade.jpg'
        WHEN 'Signature Parfum' THEN 'https://cdn.dummyjson.com/product-images/fragrances/chanel-coco-noir-eau-de/2.webp'
        WHEN 'Ceramic Vase' THEN 'https://intermobili.com.ar/3742-home_default/jarron-vena-b.jpg'
        WHEN 'Wall Frame' THEN 'https://assets.pbimgs.com/pbimgs/rk/images/dp/wcm/202346/0018/stowe-modern-metal-frames-14x17-l.jpg'
        WHEN 'Leather Wallet' THEN 'https://www.stanleather.in/cdn/shop/files/STAN_Striker_Mens_RFID_Leather_Wallet_Flatlay_Chestnut_Brown.jpg?v=1769435791&width=1946'
        WHEN 'Desk Organizer' THEN 'https://image.made-in-china.com/2f0j00syThYeCGYlpz/Hand-Craft-Pen-Stand-Wooden-Desk-Organizer.jpg'
        WHEN 'Áo thun cotton basic' THEN 'https://media.rs-online.com/image/upload/bo_1.5px_solid_white%2Cb_auto%2Cc_pad%2Cdpr_2%2Cf_auto%2Ch_399%2Cq_auto%2Cw_710/c_pad%2Ch_399%2Cw_710/R2706504-01?pgw=1'
        WHEN 'Áo khoác denim classic' THEN 'https://cdn.photogpt.io/photogpt/static/ai-ghost-mannequin/angle-front.webp'
        WHEN 'Túi đeo chéo mini' THEN 'https://safaribags.com/cdn/shop/files/1_348d8d3a-4645-40ad-bc27-59839b4fb044_1024x.jpg?v=1710843243'
        WHEN 'Áo cardigan dệt kim' THEN 'https://cf-assets-thredup.thredup.com/assets/629085563/retina.jpg'
        WHEN 'Đèn ngủ gỗ sồi' THEN 'https://vakkerlight.com/cdn/shop/products/Upsala_Ekeby_Table_Lamp_4_1800x1800.jpg?v=1723605992'
        WHEN 'Bộ ly gốm thủ công' THEN 'https://files.empikfoto.pl/static/1869/kubek-mini_661bad8c62def.jpg'
        WHEN 'Khay tre đa năng' THEN 'https://www.restaurantware.com/cdn/shop/files/RWB0451-1-LR.jpg?v=1728013425&width=600'
        WHEN 'Chăn cotton waffle' THEN 'https://static.brw.pl/brw/img/produkt/836598/koc-narzuta-125-x-150-cm-bawelniany-bezowy-slarge.jpg'
        WHEN 'Serum cấp ẩm Hyaluronic' THEN 'https://www.moha.co.in/cdn/shop/files/MOHA-HYDRATING-FACE-SERUM-30-ML-FRONT.jpg?v=1753788981'
        WHEN 'Kem chống nắng SPF50' THEN 'https://www.organiercare.com/cdn/shop/files/sunscreen_05ad1714-a3c8-4e38-ac84-545a5a44f4e2.jpg?v=1766762081&width=1946'
        WHEN 'Sữa rửa mặt dịu nhẹ' THEN 'https://images.squarespace-cdn.com/content/v1/5ba7795b523958cb2b1291e8/1695731764493-JT4AWFQT8O3NXBQ1LCB5/Product%2B%281%2Bof%2B6%29.JPG?format=1000w'
        WHEN 'Son tint dưỡng nhẹ' THEN 'https://fashioncolour.in/cdn/shop/files/6_c0140ee4-7d2d-4381-b60d-1b6b0ad6c648.jpg?v=1782382668&width=1946'
    END
    FROM products p
    WHERE p.id = pi.product_id
)
WHERE pi.public_id LIKE 'external-demo:%'
  AND EXISTS (
      SELECT 1 FROM products p
      WHERE p.id = pi.product_id
        AND p.name IN (
        'Canvas Tote Bag',
        'Linen Shirt',
        'Brass Table Lamp',
        'Signature Parfum',
        'Ceramic Vase',
        'Wall Frame',
        'Leather Wallet',
        'Desk Organizer',
        'Áo thun cotton basic',
        'Áo khoác denim classic',
        'Túi đeo chéo mini',
        'Áo cardigan dệt kim',
        'Đèn ngủ gỗ sồi',
        'Bộ ly gốm thủ công',
        'Khay tre đa năng',
        'Chăn cotton waffle',
        'Serum cấp ẩm Hyaluronic',
        'Kem chống nắng SPF50',
        'Sữa rửa mặt dịu nhẹ',
        'Son tint dưỡng nhẹ'
        )
  );
