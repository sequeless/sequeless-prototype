import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CoreApi } from './core-api';

describe('CoreApi', () => {
  let component: CoreApi;
  let fixture: ComponentFixture<CoreApi>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CoreApi],
    }).compileComponents();

    fixture = TestBed.createComponent(CoreApi);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
