import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Ontology } from './ontology';

describe('Ontology', () => {
  let component: Ontology;
  let fixture: ComponentFixture<Ontology>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Ontology],
    }).compileComponents();

    fixture = TestBed.createComponent(Ontology);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
