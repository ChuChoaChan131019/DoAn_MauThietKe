-- Store demo images on Cloudinary (PROD 10); preserve shop-uploaded images.

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079156/senvia/merchant/product-images/demo/demo-001.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-001'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://images.squarespace-cdn.com/content/v1/5d11fd45b9e7020001a6a368/bcd00582-068c-4bdd-bea8-f3ffeeb1b796/SH-66_2.png';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079156/senvia/merchant/product-images/demo/demo-002.png',
    public_id = 'senvia/merchant/product-images/demo/demo-002'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://jpidisplay.com/cdn/shop/files/12-x-8-bam-boo-natural-bamboo-modern-serving-tray-with-handles-main_394x.png?v=1683843236';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079156/senvia/merchant/product-images/demo/demo-003.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-003'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://barkhabeauty.com/cdn/shop/files/pinkiepie_878x_crop_center.jpg?v=1760428768';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079156/senvia/merchant/product-images/demo/demo-004.png',
    public_id = 'senvia/merchant/product-images/demo/demo-004'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.pngitem.com/pimgs/m/558-5581363_denim-jacket-men-png-transparent-png.png';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079158/senvia/merchant/product-images/demo/demo-005.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-005'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://assets.pbimgs.com/pbimgs/rk/images/dp/wcm/202234/0513/stowe-modern-metal-frames-13-z.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079158/senvia/merchant/product-images/demo/demo-006.png',
    public_id = 'senvia/merchant/product-images/demo/demo-006'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://ship.erewhon.com/cdn/shop/files/HYDRATINGSHEETMASK_PERSONALCARE_BEAUTY_SHOPIFY_1.png?v=1769795076';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079158/senvia/merchant/product-images/demo/demo-007.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-007'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/1.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079158/senvia/merchant/product-images/demo/demo-008.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-008'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://media.homeboxstores.com/i/homebox/168127741-168127741-HMBX21082025N_05-2100.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079159/senvia/merchant/product-images/demo/demo-009.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-009'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.organiercare.com/cdn/shop/files/sun-screen.webp?v=1766762081&width=1920';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079161/senvia/merchant/product-images/demo/demo-010.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-010'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.fotoboxpro.com/wp-content/uploads/kot-pantolon-dekupe-fotograf-cekimi-zara-tarzi-1.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079160/senvia/merchant/product-images/demo/demo-011.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-011'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.petite-plume.com/cdn/shop/files/HETHRBEI_Beige_1_b5049474-4b00-442f-a58e-d8048c1928de.jpg?v=1727195075&width=2400';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079160/senvia/merchant/product-images/demo/demo-012.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-012'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.tirabeauty.com/v2/billowing-snowflake-434234/tira-p/wrkr/products/pictures/item/free/original/YyIgl4MgtQ-1072684_1.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079161/senvia/merchant/product-images/demo/demo-013.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-013'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn11.bigcommerce.com/s-p6270ymd/images/stencil/1280x1280/products/12766/126944/2__09146.1679332779.jpg?c=2%3Fimbypass%3Don';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079162/senvia/merchant/product-images/demo/demo-014.png',
    public_id = 'senvia/merchant/product-images/demo/demo-014'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://stofferhome.com/cdn/shop/products/ScreenShot2022-04-19at11.59.20AM_1800x1800.png?v=1710437410';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079162/senvia/merchant/product-images/demo/demo-015.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-015'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/1.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079163/senvia/merchant/product-images/demo/demo-016.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-016'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://resource.tmix.jp/product_specifications/image_6b178b616bb3c353cd39998edb2c6cc8a9daea1afa5338cc2df4740c71cdc24e.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079163/senvia/merchant/product-images/demo/demo-017.png',
    public_id = 'senvia/merchant/product-images/demo/demo-017'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://bouquetier.fr/cdn/shop/files/DEQ21WES16712-176543_d24ec9e8db3ec1d413da03b7a3a5f518_dtl_1_1.png?v=1757873838&width=2400';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079164/senvia/merchant/product-images/demo/demo-018.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-018'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://dermasation.com/cdn/shop/files/URS04233.jpg?v=1758377170&width=3840';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079164/senvia/merchant/product-images/demo/demo-019.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-019'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/tops/blue-frock/1.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079165/senvia/merchant/product-images/demo/demo-020.png',
    public_id = 'senvia/merchant/product-images/demo/demo-020'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://img.pikbest.com/png-images/20241219/a-simple-white-ceramic-mug-with-a-smooth-finish-displayed-on-clean-background-_11273816.png%21sw800';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079166/senvia/merchant/product-images/demo/demo-021.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-021'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.shophalaal.com/cdn/shop/files/Untitled-1_2_1200x1200.jpg?v=1737283450';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079166/senvia/merchant/product-images/demo/demo-022.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-022'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.convertri.com/d72e79cc-9341-11ea-abef-0697e5ca793e/aadfdd482725ce16c237cc8cf6f9a6a9759d6e14/flat-lay-sweater-front-view_4.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079166/senvia/merchant/product-images/demo/demo-023.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-023'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://media.homecentre.com/i/homecentre/167226630-167226630-HC11122024_01-2100.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079167/senvia/merchant/product-images/demo/demo-024.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-024'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.mera-wellness.com/cdn/shop/files/MeraHydratingFaceSerum_1024x.jpg?v=1696997346';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079167/senvia/merchant/product-images/demo/demo-025.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-025'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.rickracktextiles.com/cdn/shop/files/SandersButtonUpFlatlayShortSleeve_1100x.jpg?v=1771964794';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079167/senvia/merchant/product-images/demo/demo-026.png',
    public_id = 'senvia/merchant/product-images/demo/demo-026'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://jpidisplay.com/cdn/shop/files/17-75-x-13-bam-boo-natural-bamboo-large-serving-tray-with-handles.png?v=1741649554';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079168/senvia/merchant/product-images/demo/demo-027.png',
    public_id = 'senvia/merchant/product-images/demo/demo-027'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://barkhabeauty.com/cdn/shop/files/Apple-Bloom_Open_Lip-Tint.webp?v=1758189514&width=3840';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079169/senvia/merchant/product-images/demo/demo-028.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-028'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.thebudgetbabe.com/uploads/2018/201801/universalthreadstargetlookbook_-_14.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079169/senvia/merchant/product-images/demo/demo-029.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-029'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://assets.pbimgs.com/pbimgs/rk/images/dp/wcm/202610/0536/reese-frames-12-o.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079171/senvia/merchant/product-images/demo/demo-030.png',
    public_id = 'senvia/merchant/product-images/demo/demo-030'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://media.gazelook.id/product_image/product_imageDPz3.png';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079170/senvia/merchant/product-images/demo/demo-031.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-031'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/2.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079171/senvia/merchant/product-images/demo/demo-032.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-032'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cb.scene7.com/is/image/Crate/CorfuWhtTblLmpDrumShdROS22/%24web_plp_card_hires%24/240201121418/corfu-cream-table-lamp-with-linen-drum-shade.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079171/senvia/merchant/product-images/demo/demo-033.png',
    public_id = 'senvia/merchant/product-images/demo/demo-033'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://purelogica.in/cdn/shop/files/T3_care_post_76.png?v=1775638112';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079172/senvia/merchant/product-images/demo/demo-034.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-034'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://orbitvu.fr/wp-content/uploads/2022/02/typical-flat-lay-arrangement-produt-photography.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079173/senvia/merchant/product-images/demo/demo-035.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-035'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://i.ebayimg.com/images/g/Rw4AAOSwkw9jGdHS/s-l1600.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079175/senvia/merchant/product-images/demo/demo-036.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-036'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://assets.woolworthsstatic.co.za/Face-Toner-for-Normal-and-Combination-Skin-250-ml-6009217061358.jpg?V=%244Kz&o=eyJidWNrZXQiOiJ3dy1vbmxpbmUtaW1hZ2UtcmVzaXplIiwia2V5IjoiaW1hZ2VzL2VsYXN0aWNlcmEvcHJvZHVjdHMvaGVyby8yMDIwLTA3LTEwLzYwMDkyMTcwNjEzNThfaGVyby5qcGcifQ';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079173/senvia/merchant/product-images/demo/demo-037.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-037'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn11.bigcommerce.com/s-p6270ymd/images/stencil/1280x1280/products/12766/100333/DSC_0769__39162.1582041456.jpg?c=2%3Fimbypass%3Don';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079173/senvia/merchant/product-images/demo/demo-038.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-038'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://earlysettler.com.au/cdn/shop/files/14e9fccd3e93ccd8ffd52ee956e675e8419d1c1b_90024906_01_web_2048x.jpg?v=1771804375';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079175/senvia/merchant/product-images/demo/demo-039.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-039'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/2.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079176/senvia/merchant/product-images/demo/demo-040.png',
    public_id = 'senvia/merchant/product-images/demo/demo-040'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.blanks4brands.com/cdn/shop/files/2_a59fb02d-0558-4ac6-9eb5-604b76bc7bec.png?v=1779749245';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079176/senvia/merchant/product-images/demo/demo-041.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-041'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://images.tcdn.com.br/img/img_prod/1464992/vaso_gota_londres_branco_1023_1_05d1d8a5d088f3b27c8078808f98b7d9.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079177/senvia/merchant/product-images/demo/demo-042.png',
    public_id = 'senvia/merchant/product-images/demo/demo-042'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://amboskincare.com/cdn/shop/files/new_900x900.webp?v=1695809381';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079177/senvia/merchant/product-images/demo/demo-043.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-043'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/tops/blue-frock/2.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079180/senvia/merchant/product-images/demo/demo-044.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-044'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://original-naire.com/web-box/upload/item/191141.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079178/senvia/merchant/product-images/demo/demo-045.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-045'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://grancestore.com/cdn/shop/files/WhatsAppImage2026-07-07at3.46.06AM_2.webp?v=1783421348&width=1445';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079179/senvia/merchant/product-images/demo/demo-046.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-046'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://product.hstatic.net/200000201725/product/z4979288927353_deb9c068095f2116fe7089e415a7ffde_99ff75a6dc764ecb91d3aa9f6cb90d1a_master.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079181/senvia/merchant/product-images/demo/demo-047.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-047'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://bamboofromvietnam.com/upload/product/a01-storage/a01-basket/b02/39-1.jpg?v=1.0.0';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079180/senvia/merchant/product-images/demo/demo-048.png',
    public_id = 'senvia/merchant/product-images/demo/demo-048'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://mymeant.com/cdn/shop/files/MEANT_Hydrating_Face_Serum_with_Advanced_Hyaluronic_Acid_for_Intense_Hydration_Bottle.png?v=1749749848&width=1445';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079181/senvia/merchant/product-images/demo/demo-049.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-049'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.shopify.com/s/files/1/0207/9406/files/01_Linen_Shirt_Oatmeal_Flat.jpg?v=1745325701&width=600';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079182/senvia/merchant/product-images/demo/demo-050.png',
    public_id = 'senvia/merchant/product-images/demo/demo-050'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://jpidisplay.com/cdn/shop/files/20-x-13-bam-boo-natural-bamboo-large-serving-tray-with-handles.png?v=1762528415';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079183/senvia/merchant/product-images/demo/demo-051.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-051'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn2.blanxer.com/uploads/654dc303c8ce9e2df20d61fe/product_image-tints-%281%29-6299.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079183/senvia/merchant/product-images/demo/demo-052.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-052'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.vestiai.com.br/landing/before-after/antes-jaqueta.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079184/senvia/merchant/product-images/demo/demo-053.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-053'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.potterybarn.co.uk/site/PB/Product%20Images/stowe-modern-metal-frames-11-x-13-202520-0005-stowe-modern-metal-frames-11x13-3-z.jpg?resizeh=960&resizeid=70&resizew=960';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079184/senvia/merchant/product-images/demo/demo-054.png',
    public_id = 'senvia/merchant/product-images/demo/demo-054'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://dtgxwmigmg3gc.cloudfront.net/imagery/assets/derivations/icon/512/512/true/eyJpZCI6ImM5MzQ4YzJiNTI1MTllMGZhZTk2NzZhMmFmNGE2ZWQ3Iiwic3RvcmFnZSI6InB1YmxpY19zdG9yZSJ9?signature=6b1f1d8ffe830909513fe10220f0a6c124e533934c34eb60ca0de30e693e3547';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079184/senvia/merchant/product-images/demo/demo-055.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-055'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/3.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079185/senvia/merchant/product-images/demo/demo-056.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-056'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.sklum.com/es/wk/4551780/lampara-de-mesa-en-ceramica-y-tela-kenaris.jpg?cf-resize=gallery';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079186/senvia/merchant/product-images/demo/demo-057.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-057'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://dermalondon.com.pk/cdn/shop/files/sunscreen-Camera.jpg?v=1772089414';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079186/senvia/merchant/product-images/demo/demo-058.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-058'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://images.squarespace-cdn.com/content/v1/6436e68b3e41f224b8b0a966/136ba57e-557a-47e3-9c2c-70aae4b73605/trouser-for-flat-lay-photography.jpg?format=500w';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079354/senvia/merchant/product-images/demo/demo-059.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-059'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://posudacenter.ru/upload/iblock/4f4/4f4de9911435f2f9b0a35bd6d7b215df.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079186/senvia/merchant/product-images/demo/demo-060.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-060'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.mbodhii.com/cdn/shop/files/mbodhii-Hydrating-Facial-Toner-bottle.jpg?v=1765872980&width=1445';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079187/senvia/merchant/product-images/demo/demo-061.png',
    public_id = 'senvia/merchant/product-images/demo/demo-061'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.fgear.in/cdn/shop/files/2_17a06ad2-aea9-481d-983c-760a86d04cf3.png?v=1772867154&width=1066';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079286/senvia/merchant/product-images/demo/demo-062.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-062'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.adairs.com.au/globalassets/catalogs/homewares/cushions/homerepublic_6/41634_natural_zoom_01.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079188/senvia/merchant/product-images/demo/demo-063.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-063'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/fragrances/calvin-klein-ck-one/3.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079189/senvia/merchant/product-images/demo/demo-064.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-064'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://sitio2021.matizsae.com.py/1397-large_default/remeras-de-algodon-endure.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079192/senvia/merchant/product-images/demo/demo-065.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-065'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://shop.nf.net.au/Products/CNT011.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079190/senvia/merchant/product-images/demo/demo-066.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-066'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://crunchi.com/cdn/shop/files/Gentle-Facial-Cleanser-Cart-Images-01.jpg?crop=center&height=1200&v=1775496405&width=1200';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079191/senvia/merchant/product-images/demo/demo-067.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-067'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/tops/blue-frock/3.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079192/senvia/merchant/product-images/demo/demo-068.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-068'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.sitografika.com.mk/Content/dataImages/galleries/big/subl_bela_blanko_1.png';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079193/senvia/merchant/product-images/demo/demo-069.png',
    public_id = 'senvia/merchant/product-images/demo/demo-069'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.miadelmar.com/cdn/shop/products/Mia-del-Mar-Night_Cream_M2_1080x.png?v=1562008747';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079194/senvia/merchant/product-images/demo/demo-070.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-070'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://product.hstatic.net/200000201725/product/z4979288925095_941eba70efa040fcf383528c1310e36f_70000233c58744aa9dabd55ef5d6f6f8_master.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079196/senvia/merchant/product-images/demo/demo-071.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-071'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://down-br.img.susercontent.com/file/sg-11134201-7rdvu-m1enpqv7xj4o67';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079194/senvia/merchant/product-images/demo/demo-072.png',
    public_id = 'senvia/merchant/product-images/demo/demo-072'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://mymeant.com/cdn/shop/files/MEANT_Hydrating_Face_Serum_with_Advanced_Hyaluronic_Acid_for_Intense_Hydration_bottle_in_air.png?v=1772726222&width=1024';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079196/senvia/merchant/product-images/demo/demo-073.png',
    public_id = 'senvia/merchant/product-images/demo/demo-073'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://permanent.mx/cdn/shop/files/44_c5adbcca-e29f-4a47-92a0-1a488ebf4e16.png?v=1718657005&width=533';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079196/senvia/merchant/product-images/demo/demo-074.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-074'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://bamboomn.com/cdn/shop/products/bamboo-serving-tray-with-handles-bdst-001-01a.jpg?v=1628876087';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079198/senvia/merchant/product-images/demo/demo-075.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-075'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://down-br.img.susercontent.com/file/br-11134207-81z1k-mg6yuwvqiosncd';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079199/senvia/merchant/product-images/demo/demo-076.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-076'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://img77.uenicdn.com/image/upload/v1531933073/service_images/shutterstock_647008975.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079198/senvia/merchant/product-images/demo/demo-077.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-077'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://assets.pbimgs.com/pbimgs/ab/images/dp/wcm/202612/0849/stowe-modern-metal-frames-14x17-2-o.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079199/senvia/merchant/product-images/demo/demo-078.png',
    public_id = 'senvia/merchant/product-images/demo/demo-078'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://ship.erewhon.com/cdn/shop/files/HYDRATING_SHEET_MASK_PERSONAL_CARE_BEAUTY_SHOPIFY_1_1.png?crop=center&height=1080&v=1776467536&width=1080';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079200/senvia/merchant/product-images/demo/demo-079.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-079'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/mens-shoes/nike-air-jordan-1-red-and-black/4.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079201/senvia/merchant/product-images/demo/demo-080.png',
    public_id = 'senvia/merchant/product-images/demo/demo-080'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.thebaliconcept.co.uk/cdn/shop/files/SenjaLamp1.png?v=1744023389&width=1024';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079201/senvia/merchant/product-images/demo/demo-081.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-081'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://riveladermascience.com/cdn/shop/files/Rivela-Sunscreen-Lotion-SPF-50-Rivela-Dermascience-29464732106958_1024x1024.jpg?v=1755863245';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079201/senvia/merchant/product-images/demo/demo-082.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-082'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://img.magnific.com/free-photo/jeans_1203-8093.jpg?q=80&semt=ais_hybrid&w=740';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079203/senvia/merchant/product-images/demo/demo-083.png',
    public_id = 'senvia/merchant/product-images/demo/demo-083'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.sashaaworld.com/cdn/shop/files/3_12a7bb0b-29d8-427d-a736-87d0fb02c8a9.png?v=1747205583&width=1200';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079203/senvia/merchant/product-images/demo/demo-084.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-084'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://veehabeauty.com/cdn/shop/files/41XHEKOMW1L._SL1200.jpg?v=1774358626';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079203/senvia/merchant/product-images/demo/demo-085.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-085'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.xbzbrindes.com.br/img/produtos/3/Bolsa-de-Ombro-Nylon-17721-1702554987.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079205/senvia/merchant/product-images/demo/demo-086.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-086'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://bymolle.com/cdn/shop/products/by-molle-linnen-kussen-flax.jpg?v=1599637069';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079205/senvia/merchant/product-images/demo/demo-087.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-087'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/fragrances/chanel-coco-noir-eau-de/1.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079205/senvia/merchant/product-images/demo/demo-088.png',
    public_id = 'senvia/merchant/product-images/demo/demo-088'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://s.alicdn.com/%40sc04/kf/S3a9ec76553cc46618ea8984789034b44B/140g-Wholesale-Good-Price-Less-Than-1.00-Custom-logo-Printing-Blank-White-Cotton-Fabric-Round-Neck-Economy-Quality-Man-t-Shirt.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079207/senvia/merchant/product-images/demo/demo-089.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-089'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.afkaryexpo.com/web/image/product.template/13190/image_512';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079207/senvia/merchant/product-images/demo/demo-090.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-090'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://glownglee.com/cdn/shop/files/IMG_8930.jpg?v=1716918037';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079207/senvia/merchant/product-images/demo/demo-091.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-091'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/tops/blue-frock/4.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079209/senvia/merchant/product-images/demo/demo-092.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-092'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://i5.walmartimages.ca/images/Enlarge/604/555/6000207604555.jpg?odnBg=FFFFFF&odnHeight=612&odnWidth=612';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079209/senvia/merchant/product-images/demo/demo-093.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-093'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://miera.in/cdn/shop/files/MIERANIGHTCREAM.jpg?v=1743431264&width=1346';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079209/senvia/merchant/product-images/demo/demo-094.png',
    public_id = 'senvia/merchant/product-images/demo/demo-094'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://images.rawpixel.com/image_social_square/cHJpdmF0ZS9sci9pbWFnZXMvd2Vic2l0ZS8yMDI0LTA2L3Jhd3BpeGVsX29mZmljZV8zNV9waG90b19vZl9hX3Bhc3RlbF9icm93bl9jYXJkaWdhbl9odW5nX29uX2Ffd18zOWUzOGZlNC1mZmRhLTRkZmQtYjAwOS1jM2U3ZmM2OTM5NDJfMS5qcGc.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079211/senvia/merchant/product-images/demo/demo-095.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-095'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://d2lnr5mha7bycj.cloudfront.net/product-image/file/large_fd7abd69-8396-4e1c-8953-25c45aa4d592.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079211/senvia/merchant/product-images/demo/demo-096.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-096'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://bbabysuleman.com/cdn/shop/products/serum_6f50b912-a6a3-44d2-ac05-c681497dc8da.jpg?v=1712182549&width=1066';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079212/senvia/merchant/product-images/demo/demo-097.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-097'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://baseec-img-mng.akamaized.net/images/item/origin/7902e7a1e88511860e67cb5bb49476dd.jpg?im=Resize%2Cwidth%3D1280%2Ctype%3Dnormal&imformat=generic&q=90';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079213/senvia/merchant/product-images/demo/demo-098.jpg',
    public_id = 'senvia/merchant/product-images/demo/demo-098'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://ferreteriavidri.com/images/items/large/464319.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079213/senvia/merchant/product-images/demo/demo-099.png',
    public_id = 'senvia/merchant/product-images/demo/demo-099'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.mocoallure.com.mx/cdn/shop/files/4_13.png?v=1782938234&width=767';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079214/senvia/merchant/product-images/demo/demo-100.webp',
    public_id = 'senvia/merchant/product-images/demo/demo-100'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://images.squarespace-cdn.com/content/v1/63cfc207f74f3448fae9f922/401d46fe-edcb-4d60-b831-88b2b24f5014/WORK_ECOMM_153%2Bcopy%2B2.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079215/senvia/merchant/product-images/demo/base-067034942455.webp',
    public_id = 'senvia/merchant/product-images/demo/base-067034942455'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://butterflai.pro/_next/image?dpl=dpl_DrmBT4dyokLAyzPXqPEjuSDXkbBB&q=75&url=https%3A%2F%2Fgjuegaszjygmxzuscvhs.supabase.co%2Fstorage%2Fv1%2Fobject%2Fgallery_multimedia%2FBag%2Fcanvas_tote_bag_studio.webp&w=3840';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079215/senvia/merchant/product-images/demo/base-d6c4f6907b7f.webp',
    public_id = 'senvia/merchant/product-images/demo/base-d6c4f6907b7f'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.pixlio.net/assets/image/remove-wrinkles-from-photo/wrinkle-before-clothing.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079216/senvia/merchant/product-images/demo/base-a1bf66694622.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-a1bf66694622'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cb.scene7.com/is/image/Crate/CorfuWhtTblLmpLnTprShdROS22/%24web_plp_card_hires%24/240201121418/corfu-cream-table-lamp-with-linen-taper-shade.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079217/senvia/merchant/product-images/demo/base-8d143ec6576b.webp',
    public_id = 'senvia/merchant/product-images/demo/base-8d143ec6576b'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.dummyjson.com/product-images/fragrances/chanel-coco-noir-eau-de/2.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079218/senvia/merchant/product-images/demo/base-3d613e9d5488.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-3d613e9d5488'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://intermobili.com.ar/3742-home_default/jarron-vena-b.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079218/senvia/merchant/product-images/demo/base-bf142520382e.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-bf142520382e'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://assets.pbimgs.com/pbimgs/rk/images/dp/wcm/202346/0018/stowe-modern-metal-frames-14x17-l.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079218/senvia/merchant/product-images/demo/base-c10f8e59e0fb.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-c10f8e59e0fb'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.stanleather.in/cdn/shop/files/STAN_Striker_Mens_RFID_Leather_Wallet_Flatlay_Chestnut_Brown.jpg?v=1769435791&width=1946';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079220/senvia/merchant/product-images/demo/base-84812b28ff78.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-84812b28ff78'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://image.made-in-china.com/2f0j00syThYeCGYlpz/Hand-Craft-Pen-Stand-Wooden-Desk-Organizer.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079220/senvia/merchant/product-images/demo/base-f0605c079f2b.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-f0605c079f2b'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://media.rs-online.com/image/upload/bo_1.5px_solid_white%2Cb_auto%2Cc_pad%2Cdpr_2%2Cf_auto%2Ch_399%2Cq_auto%2Cw_710/c_pad%2Ch_399%2Cw_710/R2706504-01?pgw=1';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079220/senvia/merchant/product-images/demo/base-d0590d09ce20.png',
    public_id = 'senvia/merchant/product-images/demo/base-d0590d09ce20'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cdn.photogpt.io/photogpt/static/ai-ghost-mannequin/angle-front.webp';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079221/senvia/merchant/product-images/demo/base-b370df39d6f0.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-b370df39d6f0'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://safaribags.com/cdn/shop/files/1_348d8d3a-4645-40ad-bc27-59839b4fb044_1024x.jpg?v=1710843243';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079222/senvia/merchant/product-images/demo/base-d23459cf966b.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-d23459cf966b'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://cf-assets-thredup.thredup.com/assets/629085563/retina.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079223/senvia/merchant/product-images/demo/base-600709a847d4.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-600709a847d4'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://vakkerlight.com/cdn/shop/products/Upsala_Ekeby_Table_Lamp_4_1800x1800.jpg?v=1723605992';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079223/senvia/merchant/product-images/demo/base-a1e85893d8f5.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-a1e85893d8f5'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://files.empikfoto.pl/static/1869/kubek-mini_661bad8c62def.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079223/senvia/merchant/product-images/demo/base-d373b9916727.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-d373b9916727'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.restaurantware.com/cdn/shop/files/RWB0451-1-LR.jpg?v=1728013425&width=600';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079225/senvia/merchant/product-images/demo/base-7e2f263a75e8.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-7e2f263a75e8'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://static.brw.pl/brw/img/produkt/836598/koc-narzuta-125-x-150-cm-bawelniany-bezowy-slarge.jpg';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079225/senvia/merchant/product-images/demo/base-ab60843bd8f7.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-ab60843bd8f7'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.moha.co.in/cdn/shop/files/MOHA-HYDRATING-FACE-SERUM-30-ML-FRONT.jpg?v=1753788981';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079226/senvia/merchant/product-images/demo/base-1ad4ca93e58a.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-1ad4ca93e58a'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://www.organiercare.com/cdn/shop/files/sunscreen_05ad1714-a3c8-4e38-ac84-545a5a44f4e2.jpg?v=1766762081&width=1946';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079227/senvia/merchant/product-images/demo/base-432e118aac7a.webp',
    public_id = 'senvia/merchant/product-images/demo/base-432e118aac7a'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://images.squarespace-cdn.com/content/v1/5ba7795b523958cb2b1291e8/1695731764493-JT4AWFQT8O3NXBQ1LCB5/Product%2B%281%2Bof%2B6%29.JPG?format=1000w';

UPDATE product_images
SET image_url = 'https://res.cloudinary.com/cewdqekr/image/upload/v1792079227/senvia/merchant/product-images/demo/base-400ddd8b33ce.jpg',
    public_id = 'senvia/merchant/product-images/demo/base-400ddd8b33ce'
WHERE public_id LIKE 'external-demo:%'
  AND image_url = 'https://fashioncolour.in/cdn/shop/files/6_c0140ee4-7d2d-4381-b60d-1b6b0ad6c648.jpg?v=1782382668&width=1946';
