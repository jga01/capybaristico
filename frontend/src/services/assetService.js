// A hardcoded list of all image URLs from the backend's GameService.
// In a production app, this list could be fetched from an API endpoint
// to avoid updating the frontend every time a new card is added.
const ALL_ASSET_URLS = [
    // Card Back
    '/assets/cards_images/back.png',
    // Table Texture
    '/assets/textures/table.png',
    // Card Fronts
    '/assets/cards_images/kahina.png',
    '/assets/cards_images/train.png',
    '/assets/cards_images/leonico.png',
    '/assets/cards_images/floppy.png',
    '/assets/cards_images/swettie.png',
    '/assets/cards_images/aop.png',
    '/assets/cards_images/aral.png',
    '/assets/cards_images/jamestiago.png',
    '/assets/cards_images/totem.png',
    '/assets/cards_images/rossome.png',
    '/assets/cards_images/menino_veneno.png',
    '/assets/cards_images/crazysoup.png',
    '/assets/cards_images/appleofecho.png',
    '/assets/cards_images/hoffman.png',
    '/assets/cards_images/ph.png',
    '/assets/cards_images/caioba.png',
    '/assets/cards_images/tomate.png',
    '/assets/cards_images/gloire.png',
    '/assets/cards_images/olivio.png',
    '/assets/cards_images/makachu.png',
    '/assets/cards_images/chemadam.png',
    '/assets/cards_images/apolo.png',
    '/assets/cards_images/realistjames.png',
    '/assets/cards_images/kizer.png',
    '/assets/cards_images/ariel.png',
    '/assets/cards_images/famous.png',
    '/assets/cards_images/ggaego.png',
    '/assets/cards_images/nox.png',
    '/assets/cards_images/hungrey.png',
    '/assets/cards_images/andgames.png',
    '/assets/cards_images/98pm.png',
];
export const getGameAssetUrls = () => {
    return ALL_ASSET_URLS;
};