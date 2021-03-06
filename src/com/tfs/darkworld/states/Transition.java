package com.tfs.darkworld.states;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

import rafgfxlib.GameHost;
import rafgfxlib.GameHost.GFMouseButton;
import rafgfxlib.GameState;

// Posebno stanje za animirane tranzicije
public class Transition extends GameState {
	/* DoomTransition state params */

	/*
	 * Odredjuje na koliko podslika ce se startImage izdeliti, veci broj daje
	 * bolji efekat meltinga
	 */
	private static int COLUMNS = 200;

	private int columnWidth;
	/*
	 * Odredjuje maksimalnu visniu offseta, visine koja mora biti pojedena pre
	 * nego sto kolona krene stvarno da pada tako sto joj se menja y koordinata
	 * koja se nalazi u heights nizu
	 */
	private double maxOffset = 200;
	/* Maksimalna razlika u visini izmedju dve kolone */
	private double maxDiff = 90;
	private double fallSpeed = 10;

	/*
	 * Sadrzi podslike slike na kojoj se vrsi tranzicija Ima ih onoliko koliko
	 * je definisano u parametru COLUMNS
	 */
	private ArrayList<BufferedImage> columns;
	/*
	 * Sadrzi offsete koji se moraju potrositi pre nego sto krenemo da menjamo
	 * visinu na kojoj iscrtavamo sliku
	 */
	private double[] offsets;
	/* Sadrzi visine iscrtavanja y koordinate za svaku sliku u nizu columns */
	private double[] heights;

	boolean done = true;

	/* end Doom state transition params */

	/* BoxTransition params */

	private static final int BOX_SIDE_LENGTH = 40;

	private ArrayList<Box> boxes;
	
	private Random random;

	/* end BoxTransition params */

	// Dvije slike u kojim cuvamo snapshot trenutnog i sljedeceg stanja
	private BufferedImage startImage = null;
	private BufferedImage endImage = null;

	// Stanje animacije, od 0 do 1
	private float position = 0.0f;
	private float speed = 0.02f;

	// Staticna instanca, ova klasa treba da bude singleton
	private static Transition instance;

	// Trenutno implementirane vrste tranzicije
	public static enum TransitionType {
		Crossfade, ZoomIn, ZoomOut, SwipeLeft, SwipeRight, Doom, Box
	};

	// Trenutno odabrana vrsta tranzicije
	private TransitionType type = TransitionType.Crossfade;

	// Stanje na koje treba prijeci na kraju tranzicije
	private GameState nextState = null;

	public Transition(GameHost host) {
		super(host);

		// Slike unaprijed konstruisemo, jer ce uvijek biti istih dimenzija
		// (prozor se ne moze
		// resizeovati) i koristimo RGB, bez alpha kanala
		startImage = new BufferedImage(host.getWidth(), host.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
		endImage = new BufferedImage(host.getWidth(), host.getHeight(), BufferedImage.TYPE_3BYTE_BGR);

		instance = this;

		/* DoomTransition params */

		columnWidth = 800 / COLUMNS;

		columns = new ArrayList<>();
		offsets = new double[COLUMNS];
		heights = new double[COLUMNS];

		prepareOffsets();
		
		/* BoxTransition params */
		random = new Random();
		boxes = new ArrayList<>();
		
		for (int i = 0; i < startImage.getHeight() / BOX_SIDE_LENGTH; i++) {
			for (int j = 0; j < startImage.getWidth() / BOX_SIDE_LENGTH; j++) {
				boxes.add(new Box(j * BOX_SIDE_LENGTH, i * BOX_SIDE_LENGTH,
						startImage.getSubimage(j * BOX_SIDE_LENGTH, i * BOX_SIDE_LENGTH, BOX_SIDE_LENGTH, BOX_SIDE_LENGTH)));
			}
		}
	}

	// Staticni poziv, radi lakse upotrebe, pokrenuce proces tranzicije
	public static void transitionTo(String nextStateName, TransitionType type, float seconds) {
		transitionTo(nextStateName, type, seconds, null);
	}

	public static void transitionTo(String nextStateName, TransitionType type, float seconds,
			ITransitionRoutine routine) {

		if (routine != null) {
			new Thread(new Runnable() {

				@Override
				public void run() {
					routine.doRoutine();
				}
			}).start();
		}

		// Trazimo stanje po imenu
		instance.nextState = instance.host.getState(nextStateName);

		// Ako nema ciljnog stanja, onda nemamo sta da radimo
		if (instance.nextState == null)
			return;

		// Pozicija tranzicije je na pocetku nula
		instance.position = 0.0f;

		// Upisujemo vrstu tranzicije
		instance.type = type;

		// Racunamo koliki inkrement treba da bude, da bismo za zadati broj
		// sekundi i trenutni FPS stigli od 0 do 1
		instance.speed = 1.0f / (seconds * instance.host.getUpdateRate());

		// Trazimo da se trenutno stanje iscrta u startImage
		instance.host.getCurrentState().renderSnapshot(instance.startImage);

		// Trazimo da se sljedece stanje iscrta u endImage
		instance.nextState.renderSnapshot(instance.endImage);

		/* DoomTransition state params */

		instance.columns.clear();

		for (int i = 0; i < COLUMNS; i++) {
			instance.columns
					.add(instance.startImage.getSubimage(i * instance.columnWidth, 0, instance.columnWidth, 600));
		}

		instance.heights = new double[COLUMNS];
		instance.prepareOffsets();
		
		/* BoxTransition state params */
		for (int i = 0; i < instance.startImage.getHeight() / BOX_SIDE_LENGTH; i++) {
			for (int j = 0; j < instance.startImage.getWidth() / BOX_SIDE_LENGTH; j++) {
				instance.boxes.add(new Box(j * BOX_SIDE_LENGTH, i * BOX_SIDE_LENGTH,
						instance.startImage.getSubimage(j * BOX_SIDE_LENGTH, i * BOX_SIDE_LENGTH, BOX_SIDE_LENGTH, BOX_SIDE_LENGTH)));
			}
		}

		// I sada prelazimo u stanje tranzicije, sto znaci da se azuriranja
		// i iscrtavanja obavljaju samo u ovom stanju, dokle god ne prepustimo
		// kontrolu sljedecem stanju (sto se desava kada position stigne do 1).
		instance.host.setState(instance);
	}

	@Override
	public boolean handleWindowClose() {
		return true;
	}

	@Override
	public String getName() {
		return "transition";
	}

	@Override
	public void resumeState() {
		// Neke vrste tranzicija ce otrkiti pozadinu, pa je postavljamo na crno
		host.setBackgroundClear(true);
		host.setBackgroundClearColor(Color.black);
	}

	@Override
	public void suspendState() {
	}

	@Override
	public void render(Graphics2D g, int sw, int sh) {
		// Radi sto cisce i jednostavnije implementacije, ne koristimo nikakve
		// dodatne
		// promjenjive stanja, vec sve animacije tranzicije pisemo kao funkcije
		// promjenjive
		// position, tj. ovdje na licu mjesta izracunavamo sve pozicije i
		// velicine oslanjajuci
		// se samo na tu promjenjivu. Eventualne dodatne tranzicije bi bilo
		// dobro implementirati
		// na isti nacin, kako bi se zadrzala jednostavnost.
		switch (type) {
		case Crossfade:
			// Pocetnu sliku crtamo potpuno vidljivu
			g.drawImage(startImage, 0, 0, null);

			// Drugu sliku crtamo sa alpha providnoscu koja zavisi od pozicije
			// animacije
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, position));
			g.drawImage(endImage, 0, 0, null);
			break;

		case SwipeLeft:
			// Pocetna slika se smanjuje i pomijera lijevo, izlazeci iz ekrana
			g.drawImage(startImage, (int) (0 - position * (host.getWidth())),
					(int) (position * host.getHeight() * 0.25f), (int) (host.getWidth() * (1.0f - position * 0.5f)),
					(int) (host.getHeight() * (1.0f - position * 0.5f)), null);

			// Krajnja slika se povecava i pomijera lijevo, ulazeci u ekran
			g.drawImage(endImage, (int) (host.getWidth() - position * (host.getWidth())),
					(int) ((1.0f - position) * host.getHeight() * 0.25f),
					(int) (host.getWidth() * (0.5f + position * 0.5f)),
					(int) (host.getHeight() * (0.5f + position * 0.5f)), null);
			break;

		case SwipeRight:
			g.drawImage(startImage, (int) (0 + position * (host.getWidth())),
					(int) (position * host.getHeight() * 0.25f), (int) (host.getWidth() * (1.0f - position * 0.5f)),
					(int) (host.getHeight() * (1.0f - position * 0.5f)), null);
			g.drawImage(endImage, (int) (-(1.0f - position) * (host.getWidth())),
					(int) ((1.0f - position) * host.getHeight() * 0.25f),
					(int) (host.getWidth() * (0.5f + position * 0.5f)),
					(int) (host.getHeight() * (0.5f + position * 0.5f)), null);
			break;

		case ZoomIn:
			// Prva slika se crta 1:1, potpuno vidljivo
			g.drawImage(startImage, 0, 0, null);

			// Druga slika polako postaje vidljiva i uvecava se od centra do
			// pune velicine
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, position));
			g.drawImage(endImage, (int) ((1.0f - position) * (host.getWidth() / 2)),
					(int) ((1.0f - position) * (host.getHeight() / 2)), (int) (position * host.getWidth()),
					(int) (position * host.getHeight()), null);
			break;

		case ZoomOut:
			// Druga slika je 1:1 pozadina
			g.drawImage(endImage, 0, 0, null);

			// Preko nje crtamo pocetnu sliku koja se polako smanjuje i postaje
			// providna
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f - position));
			g.drawImage(startImage, (int) (position * (host.getWidth() / 2)), (int) (position * (host.getHeight() / 2)),
					(int) ((1.0f - position) * host.getWidth()), (int) ((1.0f - position) * host.getHeight()), null);
			break;
		case Doom:
			// Druga slika je 1:1 pozadina
			g.drawImage(endImage, 0, 0, null);
			for (int i = 0; i < COLUMNS; i++) {
				g.drawImage(columns.get(i), i * columnWidth, (int) heights[i], null);
				// g.drawRect(i*columnWidth, 0, columnWidth, 600);
			}
			break;

		case Box:
			g.drawImage(endImage, 0, 0, null);

			for (int i = 0; i < boxes.size(); i++) {
				g.drawImage(boxes.get(i).image, (int) boxes.get(i).x, (int) boxes.get(i).y, null);
			}
			break;
		}

	}

	@Override
	public void update() {
		// U svakom koraku inkrementujemo poziciju za speed
		position += speed;

		if (type == TransitionType.Doom && position < 1.0f) {
			melt();
		}
		
		if (type == TransitionType.Box && position < 1.0f){
			updateBoxes();
		}

		// Ako smo stigli do 1, treba da prijedjemo na iduce stanje
		if (position >= 1.0f) {
			// Alpha veca od 1 pravi exceptione, pa radimo korekciju
			position = 1.0f;

			// Prebacujemo se na zadato stanje
			host.setState(nextState);
		}

	}

	@Override
	public void handleMouseDown(int x, int y, GFMouseButton button) {
	}

	@Override
	public void handleMouseUp(int x, int y, GFMouseButton button) {
	}

	@Override
	public void handleMouseMove(int x, int y) {
	}

	@Override
	public void handleKeyDown(int keyCode) {
	}

	@Override
	public void handleKeyUp(int keyCode) {
	}

	/* Metode za Doom transition */

	private void prepareOffsets() {
		for (int i = 0; i < COLUMNS; i++) {
			/*
			 * obradjujemo prvu kolonu kao specijalni slucaj jer se offset
			 * trenutnog racuna prema offsetu prethodnika
			 */
			if (i == 0) {
				offsets[i] = -Math.random() * maxOffset;
			} else {
				offsets[i] = offsets[i - 1] + Math.random() * maxDiff - maxDiff / 2;
			}

			/*
			 * Normalizujemo offsete tako da nijedan nije veci od 0, odnosno
			 * prisutan na ekranu u vreme iscrtavanja i da nije veci od
			 * maksimalne dozvoljene devijacije
			 */

			if (offsets[i] > 0) {
				offsets[i] = 0;
			} else if (offsets[i] < -maxOffset) {
				offsets[i] = -maxOffset;
			}
		}
	}

	private void melt() {

		// overrideujem position
		position = 0;

		done = true;
		for (int i = 0; i < COLUMNS; i++) {

			/*
			 * Dokle god ne potrosimo offset, odnosno dok ne postane veci od
			 * nule (na pocetku su svi setovani na vrednosti >= 0)/ * visina
			 * iscrtavanja slike je 0 (default vrednostu heights array-u)
			 * 
			 * Kada vrednost offseta padne ispod nule, pocinjemo da menjamo
			 * visinu na kojoj iscrtavamo sliku
			 * 
			 * Kada sve vrednosti u nizu budu vece od visine ekrana + mali
			 * offset znamo da se tranzicija izvrsila
			 */

			if (offsets[i] <= 0) {
				offsets[i] += fallSpeed;

				if (done) {
					done = false;
				}

			} else {
				heights[i] += fallSpeed;
				if (done) {
					done = (heights[i] < 620) ? false : true;
				}
			}
		}

		if (done) {
			position = 1;
		}
	}
	
	private void updateBoxes(){
		
		position = 0f;
		
		if (boxes.size() == 2){
			position = 1.0f;
			return;
		}
		
		boxes.remove(random.nextInt(boxes.size()-1));
		boxes.remove(random.nextInt(boxes.size()-1));
	}

	static class Box {

		public Box(double x, double y, BufferedImage image) {
			this.x = x;
			this.y = y;
			this.image = image;
		}

		double x;
		double y;
		BufferedImage image;
	}

}
